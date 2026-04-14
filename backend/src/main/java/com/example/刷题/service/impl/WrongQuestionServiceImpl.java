package com.example.刷题.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.刷题.entity.WrongQuestion;
import com.example.刷题.mapper.WrongQuestionMapper;
import com.example.刷题.service.WrongQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 错题服务实现类
 */
@Service
@RequiredArgsConstructor
public class WrongQuestionServiceImpl implements WrongQuestionService {
    
    private final WrongQuestionMapper wrongQuestionMapper;
    
    @Override
    public IPage<WrongQuestion> getWrongQuestions(Long userId, Integer page, Integer size) {
        Page<WrongQuestion> pageParam = new Page<>(page, size);
        return wrongQuestionMapper.selectByUserId(pageParam, userId);
    }
    
    @Override
    public boolean addWrongQuestion(Long userId, Long questionId, String userAnswer) {
        WrongQuestion existing = wrongQuestionMapper.selectByUserIdAndQuestionId(userId, questionId);
        if (existing != null) {
            // 更新错误次数和时间
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setLastWrongTime(LocalDateTime.now());
            existing.setUserAnswer(userAnswer);
            return wrongQuestionMapper.updateById(existing) > 0;
        } else {
            // 创建新错题记录
            WrongQuestion wrongQuestion = new WrongQuestion();
            wrongQuestion.setUserId(userId);
            wrongQuestion.setQuestionId(questionId);
            wrongQuestion.setUserAnswer(userAnswer);
            wrongQuestion.setWrongCount(1);
            wrongQuestion.setLastWrongTime(LocalDateTime.now());
            return wrongQuestionMapper.insert(wrongQuestion) > 0;
        }
    }
    
    @Override
    public boolean removeWrongQuestion(Long userId, Long id) {
        WrongQuestion wrongQuestion = wrongQuestionMapper.selectById(id);
        if (wrongQuestion != null && wrongQuestion.getUserId().equals(userId)) {
            return wrongQuestionMapper.deleteById(id) > 0;
        }
        return false;
    }
    
    @Override
    public WrongQuestion getWrongQuestion(Long userId, Long questionId) {
        return wrongQuestionMapper.selectByUserIdAndQuestionId(userId, questionId);
    }
}
