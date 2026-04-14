package com.example.刷题.controller;

import com.example.刷题.common.Result;
import com.example.刷题.entity.Favorite;
import com.example.刷题.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.example.support.SecurityUtils;

import java.util.List;

/**
 * 收藏控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    
    private final FavoriteService favoriteService;
    private final SecurityUtils securityUtils;
    
    /**
     * 获取收藏列表
     * GET /api/favorites
     */
    @GetMapping
    public Result<List<Favorite>> getFavorites() {
        log.info("请求获取收藏列表");
        Long userId = securityUtils.getCurrentUserId();
        List<Favorite> favorites = favoriteService.getFavorites(userId);
        return Result.success(favorites);
    }
    
    /**
     * 收藏题目
     * POST /api/favorites
     */
    @PostMapping
    public Result<Boolean> addFavorite(@RequestParam Long questionId) {
        log.info("请求收藏题目，questionId: {}", questionId);
        Long userId = securityUtils.getCurrentUserId();
        boolean result = favoriteService.addFavorite(userId, questionId);
        if (result) {
            return Result.success(true);
        }
        return Result.fail("收藏失败");
    }
    
    /**
     * 取消收藏
     * DELETE /api/favorites/{questionId}
     */
    @DeleteMapping("/{questionId}")
    public Result<Boolean> removeFavorite(@PathVariable Long questionId) {
        log.info("请求取消收藏，questionId: {}", questionId);
        Long userId = securityUtils.getCurrentUserId();
        boolean result = favoriteService.removeFavorite(userId, questionId);
        if (result) {
            return Result.success(true);
        }
        return Result.fail("取消收藏失败");
    }
}
