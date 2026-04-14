package com.example.刷题.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.刷题.entity.Category;
import com.example.刷题.entity.Question;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.mapper.CategoryMapper;
import com.example.刷题.mapper.QuestionMapper;
import com.example.刷题.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;

    @Override
    public List<Category> getCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getStatus, 1)
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId);
        return categoryMapper.selectList(queryWrapper);
    }

    @Override
    public List<Category> getAllCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getParentId).orderByAsc(Category::getSort).orderByAsc(Category::getId);
        return categoryMapper.selectList(queryWrapper);
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public boolean createCategory(Category category) {
        normalizeCategory(category, false);
        return categoryMapper.insert(category) > 0;
    }

    @Override
    public boolean updateCategory(Category category) {
        normalizeCategory(category, true);
        return categoryMapper.updateById(category) > 0;
    }

    @Override
    public boolean deleteCategory(Long id) {
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("请先删除该专题下的章节");
        }

        Long questionCount = questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getCategoryId, id));
        if (questionCount != null && questionCount > 0) {
            throw new BusinessException("请先处理该分类下的题目后再删除");
        }

        return categoryMapper.deleteById(id) > 0;
    }

    private void normalizeCategory(Category category, boolean isUpdate) {
        if (category == null) {
            throw new BusinessException("分类信息不能为空");
        }

        if (!StringUtils.hasText(category.getName())) {
            throw new BusinessException("分类名称不能为空");
        }

        category.setName(category.getName().trim());
        category.setDescription(StringUtils.hasText(category.getDescription()) ? category.getDescription().trim() : null);
        category.setSort(category.getSort() == null ? 1 : category.getSort());
        category.setStatus(category.getStatus() == null ? 1 : category.getStatus());
        category.setParentId(category.getParentId() == null ? 0L : category.getParentId());

        if (category.getSort() < 1) {
            throw new BusinessException("排序值必须大于 0");
        }

        if (category.getStatus() != 0 && category.getStatus() != 1) {
            throw new BusinessException("分类状态不合法");
        }

        if (isUpdate && category.getId() == null) {
            throw new BusinessException("分类ID不能为空");
        }

        if (isUpdate && category.getId().equals(category.getParentId())) {
            throw new BusinessException("分类不能选择自己作为父级");
        }

        if (category.getParentId() < 0) {
            throw new BusinessException("父级分类不合法");
        }

        if (category.getParentId() > 0) {
            Category parent = categoryMapper.selectById(category.getParentId());
            if (parent == null) {
                throw new BusinessException("父级专题不存在");
            }
            if (parent.getParentId() != null && parent.getParentId() > 0) {
                throw new BusinessException("当前仅支持专题下新增一层章节");
            }
            category.setPracticeMode(2);
        } else {
            category.setPracticeMode(category.getPracticeMode() == null ? 1 : category.getPracticeMode());
        }

        if (category.getPracticeMode() == null || (category.getPracticeMode() != 1 && category.getPracticeMode() != 2)) {
            throw new BusinessException("练习模式不合法");
        }

        if (isUpdate) {
            Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, category.getId()));
            if (childCount != null && childCount > 0 && category.getParentId() > 0) {
                throw new BusinessException("已包含章节的专题不能再移动为其他专题的章节");
            }
        }
    }
}
