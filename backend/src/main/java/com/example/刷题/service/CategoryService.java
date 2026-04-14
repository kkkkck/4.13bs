package com.example.刷题.service;

import com.example.刷题.entity.Category;

import java.util.List;

public interface CategoryService {

    List<Category> getCategories();

    List<Category> getAllCategories();

    Category getCategoryById(Long id);

    boolean createCategory(Category category);

    boolean updateCategory(Category category);

    boolean deleteCategory(Long id);
}
