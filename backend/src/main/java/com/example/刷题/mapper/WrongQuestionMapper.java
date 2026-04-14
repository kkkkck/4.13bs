package com.example.刷题.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.刷题.entity.WrongQuestion;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/**
 * 错题Mapper接口
 */
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {
    
    /**
     * 分页查询用户错题
     */
    IPage<WrongQuestion> selectByUserId(IPage<WrongQuestion> page, @Param("userId") Long userId);
    
    /**
     * 根据用户ID和题目ID查询错题记录
     */
    WrongQuestion selectByUserIdAndQuestionId(@Param("userId") Long userId, @Param("questionId") Long questionId);
}
