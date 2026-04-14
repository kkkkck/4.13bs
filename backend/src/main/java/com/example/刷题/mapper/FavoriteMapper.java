package com.example.刷题.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.刷题.entity.Favorite;
import org.apache.ibatis.annotations.Param;

/**
 * 收藏Mapper接口
 */
public interface FavoriteMapper extends BaseMapper<Favorite> {
    
    /**
     * 根据用户ID和题目ID查询收藏记录
     */
    Favorite selectByUserIdAndQuestionId(@Param("userId") Long userId, @Param("questionId") Long questionId);
    
    /**
     * 根据用户ID和题目ID删除收藏记录
     */
    int deleteByUserIdAndQuestionId(@Param("userId") Long userId, @Param("questionId") Long questionId);
}
