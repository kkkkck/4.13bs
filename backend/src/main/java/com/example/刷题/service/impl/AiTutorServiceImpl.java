package com.example.刷题.service.impl;

import com.example.刷题.config.OllamaProperties;
import com.example.刷题.dto.AiTutorMessage;
import com.example.刷题.dto.AiTutorRequest;
import com.example.刷题.dto.AiTutorResponse;
import com.example.刷题.entity.Question;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.service.AiTutorService;
import com.example.刷题.service.QuestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiTutorServiceImpl implements AiTutorService {
    // AI 答疑服务：把题库里的题干、选项、标准答案和解析一起发给本机 Ollama。
    // 这样模型是在“题库材料”范围内解释，而不是随意发挥。
    private static final String SYSTEM_PROMPT = """
            你是考研政治刷题系统中的答疑助手。只能依据题目、选项、标准答案、题库解析和解题思路解释。
            不要改变题库给出的标准答案。不要编造教材页码、政策原文、作者或数据。
            如果题目信息不足，直接说明信息不足，并给出基于现有信息的谨慎解释。
            回答要面向学生，先说明结论，再解释关键依据，最后指出常见误区。""";

    private final OllamaProperties properties;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    @Override
    public AiTutorResponse ask(AiTutorRequest request) {
        // 如果 application.yml 里关闭了 app.ai.ollama.enabled，就直接拒绝调用本地 AI。
        if (!properties.isEnabled()) {
            throw new BusinessException("本地AI功能未启用");
        }

        // AI 答疑不是让模型自由发挥，而是先查出题库里的题干、选项、答案和解析作为上下文。
        Question question = questionService.getByIdWithCache(request.getQuestionId());
        if (question == null) {
            throw new BusinessException("题目不存在");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        // Ollama 的 /api/chat 接收 messages 数组；system 负责定规则，user 负责给题目和学生问题。
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", buildQuestionContext(question, request.getUserAnswer())));
        appendHistory(messages, request.getHistory());
        messages.add(message("user", request.getMessage().trim()));

        try {
            String answer = callOllama(messages);
            return new AiTutorResponse(answer, properties.getModel());
        } catch (IOException exception) {
            throw new BusinessException("本地AI暂不可用，请确认Ollama已启动且模型已下载");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("本地AI请求已中断，请稍后重试");
        }
    }

    private String callOllama(List<Map<String, String>> messages) throws IOException, InterruptedException {
        // 这里手动用 Java HttpClient 调 Ollama，避免为了一个本地 HTTP 接口额外引入 SDK 依赖。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("stream", false);
        payload.put("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                // 默认请求 http://127.0.0.1:11434/api/chat，也就是你本机正在运行的 Ollama。
                .uri(URI.create(normalizeBaseUrl(properties.getBaseUrl()) + "/api/chat"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("本地AI调用失败，请确认Ollama模型名称配置正确");
        }

        // Ollama /api/chat 返回 JSON，其中 message.content 才是模型真正生成的答案文本。
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("本地AI未返回有效内容");
        }
        return content.trim();
    }

    private void appendHistory(List<Map<String, String>> messages, List<AiTutorMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }

        // 只带最近几轮对话，既能保持上下文，又避免 prompt 太长拖慢本地模型。
        int start = Math.max(0, history.size() - Math.max(0, properties.getMaxHistoryMessages()));
        for (AiTutorMessage item : history.subList(start, history.size())) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            String role = "assistant".equals(item.getRole()) ? "assistant" : "user";
            messages.add(message(role, item.getContent().trim()));
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildQuestionContext(Question question, String userAnswer) {
        // 这是防止 AI “跑题”的关键：把题目客观信息整理成一段上下文，要求模型基于这些信息解释。
        StringBuilder builder = new StringBuilder();
        builder.append("请基于以下题目信息答疑，不要脱离题目：\n");
        builder.append("题干：").append(nullToEmpty(question.getContent())).append('\n');
        appendOption(builder, "A", question.getOptionA());
        appendOption(builder, "B", question.getOptionB());
        appendOption(builder, "C", question.getOptionC());
        appendOption(builder, "D", question.getOptionD());
        builder.append("学生答案：").append(nullToEmpty(userAnswer)).append('\n');
        builder.append("标准答案：").append(nullToEmpty(question.getCorrectAnswer())).append('\n');
        builder.append("题库解析：").append(nullToEmpty(question.getAnalysis())).append('\n');
        builder.append("解题思路：").append(nullToEmpty(question.getSolutionStrategy())).append('\n');
        return builder.toString();
    }

    private void appendOption(StringBuilder builder, String key, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(key).append(". ").append(value.trim()).append('\n');
        }
    }

    private String nullToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "无";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://127.0.0.1:11434";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
