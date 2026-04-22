package com.example.刷题.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.刷题.dto.MockExamPaperResponse;
import com.example.刷题.entity.Question;
import com.example.刷题.dto.SubmitAnswerRequest;
import com.example.刷题.dto.SubmitAnswerResponse;
import java.util.List;
import java.util.Map;

/**
 * 题目服务接口
 */
public interface QuestionService extends IService<Question> {

    /**
     * 根据ID获取题目（使用三级缓存）
     * @param id 题目ID
     * @return 题目对象
     */
    Question getByIdWithCache(Long id);

    List<Question> getByIds(List<Long> ids);

    /**
     * 更新题目（自动清除缓存）
     * @param question 题目对象
     * @return 更新结果
     */
    boolean updateByIdWithCache(Question question);
    
    /**
     * 根据分类查询题目列表
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    IPage<Question> getQuestionsByCategory(Long categoryId, Integer page, Integer size, Integer sourceType);
    
    /**
     * 提交答案并判题
     * @param questionId 题目ID
     * @param request 提交答案请求
     * @return 判题结果
     */
    SubmitAnswerResponse submitAnswer(Long questionId, SubmitAnswerRequest request);
    
    /**
     * 查询题目列表（支持搜索和分类筛选）
     * @param page 页码
     * @param size 每页数量
     * @param keyword 关键词
     * @param categoryId 分类ID
     * @return 分页结果
     */
    Map<String, Object> getQuestionList(
            Integer page,
            Integer size,
            String keyword,
            Long categoryId,
            Integer status,
            Integer type,
            Integer difficulty,
            Integer sourceType
    );
    
    /**
     * 创建题目
     * @param question 题目对象
     */
    void createQuestion(Question question);

    boolean existsImportDuplicate(Question question);

    boolean enrichImportDuplicate(Question question);
    
    /**
     * 更新题目
     * @param question 题目对象
     */
    void updateQuestion(Question question);

    void updateQuestionStatus(Long id, Integer status);
    
    /**
     * 删除题目
     * @param id 题目ID
     */
    void deleteQuestion(Long id);

    /**
     * 生成模拟试卷
     * @param totalQuestions 目标题量
     * @return 模拟试卷
     */
    MockExamPaperResponse generateMockExam(Integer totalQuestions);
}
