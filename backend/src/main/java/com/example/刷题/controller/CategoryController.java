package com.example.刷题.controller;

import com.example.刷题.entity.Category;
import com.example.刷题.service.CategoryService;
import com.example.刷题.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    
    private final CategoryService categoryService;
    
    /**
     * 获取题目分类列表
     * GET /api/categories
     */
    @GetMapping
    public Result<List<Category>> getCategories() {
        log.info("请求获取分类列表");
        List<Category> categories = categoryService.getCategories();
        return Result.success(categories);
    }
}
