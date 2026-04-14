package com.example.刷题.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 练习记录实体类
 * 记录每次练习的详细信息
 */
@Data
@TableName("practice_record")
public class PracticeRecord {
    
    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 分类ID
     */
    private Long categoryId;
    
    /**
     * 总题数
     */
    private Integer totalQuestions;
    
    /**
     * 答对题数
     */
    private Integer correctCount;
    
    /**
     * 耗时（秒）
     */
    private Integer duration;
    
    /**
     * 创建时间（练习时间）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
