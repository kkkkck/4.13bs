package com.example.刷题.service;

import com.example.刷题.entity.WrongQuestion;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 错题服务接口
 */
public interface WrongQuestionService {
    
    /**
     * 分页查询用户错题
     */
    IPage<WrongQuestion> getWrongQuestions(Long userId, Integer page, Integer size);
    
    /**
     * 添加错题
     */
    boolean addWrongQuestion(Long userId, Long questionId, String userAnswer);
    
    /**
     * 移除错题
     */
    boolean removeWrongQuestion(Long userId, Long id);
    
    /**
     * 查询错题记录
     */
    WrongQuestion getWrongQuestion(Long userId, Long questionId);
}
