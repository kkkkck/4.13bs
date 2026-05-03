package com.example.刷题.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.ollama")
public class OllamaProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:11434";
    private String model = "deepseek-r1:7b";
    private int timeoutSeconds = 30;
    private int maxHistoryMessages = 6;
}
