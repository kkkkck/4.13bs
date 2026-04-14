package com.example.刷题.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题实体类
 */
@Data
@TableName("wrong_question")
public class WrongQuestion {
    
    /**
     * 错题记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 题目ID
     */
    private Long questionId;
    
    /**
     * 用户提交的答案
     */
    private String userAnswer;
    
    /**
     * 错误次数
     */
    private Integer wrongCount;
    
    /**
     * 最后错误时间
     */
    private LocalDateTime lastWrongTime;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
