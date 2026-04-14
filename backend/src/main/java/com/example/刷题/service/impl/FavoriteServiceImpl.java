package com.example.刷题.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.刷题.entity.Favorite;
import com.example.刷题.mapper.FavoriteMapper;
import com.example.刷题.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收藏服务实现类
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {
    
    private final FavoriteMapper favoriteMapper;
    
    @Override
    public List<Favorite> getFavorites(Long userId) {
        LambdaQueryWrapper<Favorite> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreatedAt);
        return favoriteMapper.selectList(queryWrapper);
    }
    
    @Override
    public boolean addFavorite(Long userId, Long questionId) {
        // 检查是否已收藏
        if (isFavorite(userId, questionId)) {
            return true; // 已收藏，直接返回成功
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setQuestionId(questionId);
        return favoriteMapper.insert(favorite) > 0;
    }
    
    @Override
    public boolean removeFavorite(Long userId, Long questionId) {
        return favoriteMapper.deleteByUserIdAndQuestionId(userId, questionId) > 0;
    }
    
    @Override
    public boolean isFavorite(Long userId, Long questionId) {
        Favorite favorite = favoriteMapper.selectByUserIdAndQuestionId(userId, questionId);
        return favorite != null;
    }
}
