package com.example.刷题.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.刷题.entity.Question;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 题目数据访问接口
 */
public interface QuestionMapper extends BaseMapper<Question> {
    
    /**
     * 根据分类查询题目列表
     */
    IPage<Question> selectByCategoryIds(
            IPage<Question> page,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("sourceType") Integer sourceType
    );
    
    /**
     * 查询题目列表（支持搜索和分类筛选）
     */
    IPage<Question> selectQuestionList(
            IPage<Question> page,
            @Param("keyword") String keyword,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("status") Integer status,
            @Param("type") Integer type,
            @Param("difficulty") Integer difficulty,
            @Param("sourceType") Integer sourceType
    );

    /**
     * 查询分类下所有启用题目
     */
    List<Question> selectActiveByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
    
    /**
     * 按分类统计题目数量
     */
    List<Map<String, Object>> selectQuestionCountByCategory();
    
    List<Map<String, Object>> selectQuestionCountByType();

    long countImportDuplicates(@Param("question") Question question);

    Question selectByImportHash(@Param("importHash") String importHash);
}
