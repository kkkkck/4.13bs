package com.example.刷题.dto;

import com.example.刷题.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MockExamPaperResponse {
    private Integer requestedQuestions;
    private Integer totalQuestions;
    private Integer availableQuestions;
    private Integer suggestedDurationMinutes;
    private List<Question> questions;
    private List<MockExamSection> sections;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockExamSection {
        private Long categoryId;
        private String categoryName;
        private Integer questionCount;
        private List<MockExamChapter> chapters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MockExamChapter {
        private Long categoryId;
        private String categoryName;
        private Integer questionCount;
    }
}
