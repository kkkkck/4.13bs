package com.example.刷题.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiTutorResponse {
    private String answer;
    private String model;
}
