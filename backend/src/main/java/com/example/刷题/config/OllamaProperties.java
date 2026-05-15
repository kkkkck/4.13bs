package com.example.刷题.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.ollama")
public class OllamaProperties {
    // 本地 AI 配置会自动绑定 application.yml 里的 app.ai.ollama。
    // 答辩时可以说明：项目不把题目发到云端，而是请求本机 Ollama 的 DeepSeek 模型。
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:11434";
    private String model = "deepseek-r1:7b";
    private int timeoutSeconds = 30;
    private int maxHistoryMessages = 6;
}
