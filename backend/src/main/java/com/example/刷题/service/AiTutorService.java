package com.example.刷题.service;

import com.example.刷题.dto.AiTutorRequest;
import com.example.刷题.dto.AiTutorResponse;

public interface AiTutorService {
    AiTutorResponse ask(AiTutorRequest request);
}
