package com.example.刷题.service;

import com.example.刷题.entity.Favorite;
import java.util.List;

/**
 * 收藏服务接口
 */
public interface FavoriteService {
    
    /**
     * 获取用户收藏列表
     */
    List<Favorite> getFavorites(Long userId);
    
    /**
     * 添加收藏
     */
    boolean addFavorite(Long userId, Long questionId);
    
    /**
     * 取消收藏
     */
    boolean removeFavorite(Long userId, Long questionId);
    
    /**
     * 检查是否已收藏
     */
    boolean isFavorite(Long userId, Long questionId);
}
