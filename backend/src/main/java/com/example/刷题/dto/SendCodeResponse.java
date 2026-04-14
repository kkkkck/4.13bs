package com.example.刷题.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SendCodeResponse {
    private String message;
    private String debugCode;
    private Integer expiresInSeconds;
    private Boolean mailEnabled;
}
