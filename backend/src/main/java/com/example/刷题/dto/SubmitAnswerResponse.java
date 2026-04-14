package com.example.刷题.dto;

import lombok.Data;

@Data
public class SubmitAnswerResponse {
    private boolean correct;
    private String correctAnswer;
    private String userAnswer;
    private String analysis;
    private String solutionStrategy;
    private String message;
}
