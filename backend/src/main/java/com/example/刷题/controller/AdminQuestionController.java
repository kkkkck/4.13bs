package com.example.刷题.controller;

import com.example.刷题.dto.AdminQuestionStatusRequest;
import com.example.刷题.entity.Question;
import com.example.刷题.service.QuestionService;
import com.example.刷题.util.ExcelImportUtil;
import com.example.刷题.util.QuestionImportTemplateUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getQuestionList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer sourceType) {
        return ResponseEntity.ok(questionService.getQuestionList(page, size, keyword, categoryId, status, type, difficulty, sourceType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Question> getQuestionById(@PathVariable Long id) {
        Question question = questionService.getByIdWithCache(id);
        if (question == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(question);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createQuestion(@RequestBody Question question) {
        questionService.createQuestion(question);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Question created successfully");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] content = QuestionImportTemplateUtil.buildTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=question-import-template.xls")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(content);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateQuestion(@PathVariable Long id, @RequestBody Question question) {
        question.setId(id);
        questionService.updateQuestion(question);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Question updated successfully");
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateQuestionStatus(@PathVariable Long id, @RequestBody AdminQuestionStatusRequest request) {
        questionService.updateQuestionStatus(id, request.getStatus());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Question status updated successfully");
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Question deleted successfully");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importQuestions(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Please select a file to import");
            return ResponseEntity.badRequest().body(result);
        }

        List<Question> questions = ExcelImportUtil.importQuestions(file);
        int successCount = 0;
        int duplicateCount = 0;
        List<String> errors = new ArrayList<>();

        for (Question question : questions) {
            try {
                if (questionService.existsImportDuplicate(question)) {
                    duplicateCount++;
                    continue;
                }
                questionService.createQuestion(question);
                successCount++;
            } catch (Exception ex) {
                String preview = question.getContent() == null ? "untitled question" : question.getContent();
                errors.add(preview.substring(0, Math.min(preview.length(), 24)) + ": " + ex.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("total", questions.size());
        result.put("successCount", successCount);
        result.put("duplicateCount", duplicateCount);
        result.put("failCount", errors.size());
        result.put("errors", errors.stream().limit(8).toList());
        result.put(
                "message",
                String.format(
                        "Import finished: %d succeeded, %d duplicates skipped, %d failed",
                        successCount,
                        duplicateCount,
                        errors.size()
                )
        );
        return ResponseEntity.ok(result);
    }
}
