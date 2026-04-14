package com.example.刷题.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.刷题.dto.MockExamPaperResponse;
import com.example.刷题.dto.SubmitAnswerRequest;
import com.example.刷题.dto.SubmitAnswerResponse;
import com.example.刷题.entity.Category;
import com.example.刷题.entity.Question;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.mapper.CategoryMapper;
import com.example.刷题.mapper.QuestionMapper;
import com.example.刷题.service.CacheService;
import com.example.刷题.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    private static final List<Integer> MOCK_EXAM_OBJECTIVE_TYPES = List.of(1, 5);
    private static final Map<Long, Integer> MOCK_EXAM_ROOT_WEIGHTS = buildMockExamRootWeights();
    private static final Map<Integer, Integer> MOCK_EXAM_TYPE_WEIGHTS = Map.of(1, 16, 5, 17);

    @Autowired
    private CacheService cacheService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public Question getByIdWithCache(Long id) {
        String cacheKey = "question:" + id;
        return cacheService.get(cacheKey, Question.class, () -> {
            log.info("Load question from database, id={}", id);
            return super.getById(id);
        });
    }

    @Override
    public List<Question> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> orderMap = new HashMap<>();
        List<Long> uniqueIds = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || orderMap.containsKey(id)) {
                continue;
            }
            orderMap.put(id, uniqueIds.size());
            uniqueIds.add(id);
        }

        if (uniqueIds.isEmpty()) {
            return List.of();
        }

        List<Question> questions = new ArrayList<>(super.listByIds(uniqueIds));
        questions.sort(Comparator.comparingInt(question -> orderMap.getOrDefault(question.getId(), Integer.MAX_VALUE)));
        return questions;
    }

    @Override
    public boolean updateByIdWithCache(Question question) {
        normalizeQuestion(question);
        boolean updated = super.updateById(question);
        if (updated) {
            clearQuestionCaches(question.getId());
        }
        return updated;
    }

    @Override
    public IPage<Question> getQuestionsByCategory(Long categoryId, Integer page, Integer size, Integer sourceType) {
        Page<Question> pageParam = new Page<>(page, size);
        List<Long> categoryIds = resolveCategoryIds(categoryId, true);
        if (categoryIds.isEmpty()) {
            return pageParam;
        }
        String cacheKey = "questions:category:" + categoryId + ":page:" + page + ":size:" + size + ":sourceType:" + (sourceType == null ? 0 : sourceType);
        return cacheService.get(cacheKey, IPage.class, () -> getBaseMapper().selectByCategoryIds(pageParam, categoryIds, sourceType));
    }

    @Override
    public SubmitAnswerResponse submitAnswer(Long questionId, SubmitAnswerRequest request) {
        Question question = getByIdWithCache(questionId);
        if (question == null) {
            SubmitAnswerResponse response = new SubmitAnswerResponse();
            response.setCorrect(false);
            response.setMessage("题目不存在");
            return response;
        }

        String normalizedUserAnswer = normalizeAnswer(question.getType(), request.getAnswer());
        String normalizedCorrectAnswer = normalizeAnswer(question.getType(), question.getCorrectAnswer());

        SubmitAnswerResponse response = new SubmitAnswerResponse();
        response.setUserAnswer(normalizedUserAnswer);
        response.setCorrectAnswer(normalizedCorrectAnswer);
        response.setAnalysis(question.getAnalysis());
        response.setSolutionStrategy(question.getSolutionStrategy());

        boolean correct = StringUtils.hasText(normalizedUserAnswer)
                && StringUtils.hasText(normalizedCorrectAnswer)
                && normalizedUserAnswer.equalsIgnoreCase(normalizedCorrectAnswer);
        response.setCorrect(correct);
        response.setMessage(correct ? "回答正确" : "回答错误");
        return response;
    }

    @Override
    public Map<String, Object> getQuestionList(
            Integer page,
            Integer size,
            String keyword,
            Long categoryId,
            Integer status,
            Integer type,
            Integer difficulty,
            Integer sourceType
    ) {
        Page<Question> pageParam = new Page<>(page, size);
        List<Long> categoryIds = categoryId == null ? null : resolveCategoryIds(categoryId, false);
        if (categoryId != null && (categoryIds == null || categoryIds.isEmpty())) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("records", List.of());
            empty.put("total", 0);
            empty.put("page", page);
            empty.put("size", size);
            return empty;
        }

        IPage<Question> result = getBaseMapper().selectQuestionList(pageParam, keyword, categoryIds, status, type, difficulty, sourceType);

        Map<String, Object> map = new HashMap<>();
        map.put("records", result.getRecords());
        map.put("total", result.getTotal());
        map.put("page", page);
        map.put("size", size);
        return map;
    }

    @Override
    public void createQuestion(Question question) {
        normalizeQuestion(question);
        super.save(question);
        clearQuestionCaches(question.getId());
    }

    @Override
    public boolean existsImportDuplicate(Question question) {
        normalizeQuestion(question);
        return getBaseMapper().countImportDuplicates(question) > 0;
    }

    @Override
    public void updateQuestion(Question question) {
        normalizeQuestion(question);
        super.updateById(question);
        clearQuestionCaches(question.getId());
    }

    @Override
    public void updateQuestionStatus(Long id, Integer status) {
        if (id == null) {
            throw new BusinessException("题目ID不能为空");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("题目状态只能是启用或停用");
        }

        Question question = super.getById(id);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }

        question.setStatus(status);
        super.updateById(question);
        clearQuestionCaches(id);
    }

    @Override
    public void deleteQuestion(Long id) {
        super.removeById(id);
        clearQuestionCaches(id);
    }

    @Override
    public MockExamPaperResponse generateMockExam(Integer totalQuestions) {
        int requested = totalQuestions == null ? 33 : totalQuestions;
        if (requested < 5 || requested > 100) {
            throw new BusinessException("模拟考试题量需在 5 到 100 之间");
        }

        List<Category> activeCategories = listCategories(true);
        if (activeCategories.isEmpty()) {
            throw new BusinessException("当前没有可用专题");
        }

        Map<Long, List<Category>> childrenMap = buildChildrenMap(activeCategories);
        List<Question> questionPool = getBaseMapper().selectActiveByCategoryIds(
                activeCategories.stream().map(Category::getId).toList()
        ).stream()
                .filter(question -> MOCK_EXAM_OBJECTIVE_TYPES.contains(question.getType()))
                .toList();
        Map<Long, List<Question>> questionsByCategory = questionPool.stream()
                .collect(Collectors.groupingBy(Question::getCategoryId));

        List<ExamBucket> buckets = new ArrayList<>();
        for (Category root : activeCategories) {
            if (!isRoot(root)) {
                continue;
            }

            List<ExamLeaf> leaves = new ArrayList<>();
            List<Question> rootQuestions = new ArrayList<>(questionsByCategory.getOrDefault(root.getId(), List.of()));
            if (!rootQuestions.isEmpty()) {
                leaves.add(new ExamLeaf(root, groupQuestionsByType(rootQuestions)));
            }

            for (Category child : childrenMap.getOrDefault(root.getId(), List.of())) {
                List<Question> childQuestions = new ArrayList<>(questionsByCategory.getOrDefault(child.getId(), List.of()));
                if (!childQuestions.isEmpty()) {
                    leaves.add(new ExamLeaf(child, groupQuestionsByType(childQuestions)));
                }
            }

            int available = leaves.stream().mapToInt(ExamLeaf::getAvailable).sum();
            if (available > 0) {
                buckets.add(new ExamBucket(root, leaves));
            }
        }

        if (buckets.isEmpty()) {
            throw new BusinessException("当前题库题量不足，暂时无法生成模拟试卷");
        }

        int availableQuestions = buckets.stream().mapToInt(ExamBucket::getAvailable).sum();
        int actualTotal = Math.min(requested, availableQuestions);

        Map<Integer, Integer> typeTargets = allocateWeightedCounts(
                actualTotal,
                Map.of(
                        1, buckets.stream().mapToInt(bucket -> bucket.getAvailable(1)).sum(),
                        5, buckets.stream().mapToInt(bucket -> bucket.getAvailable(5)).sum()
                ),
                MOCK_EXAM_TYPE_WEIGHTS,
                true
        );

        List<Question> selectedQuestions = new ArrayList<>();
        Map<Long, SectionAccumulator> sectionAccumulatorMap = new LinkedHashMap<>();

        for (Integer type : MOCK_EXAM_OBJECTIVE_TYPES) {
            int typeTarget = typeTargets.getOrDefault(type, 0);
            if (typeTarget <= 0) {
                continue;
            }

            Map<Long, Integer> rootCapacities = buckets.stream()
                    .filter(bucket -> bucket.getAvailable(type) > 0)
                    .collect(Collectors.toMap(
                            bucket -> bucket.category().getId(),
                            bucket -> bucket.getAvailable(type),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            Map<Long, Integer> rootAllocation = allocateWeightedCounts(typeTarget, rootCapacities, MOCK_EXAM_ROOT_WEIGHTS, true);

            for (ExamBucket bucket : buckets) {
                int rootCount = rootAllocation.getOrDefault(bucket.category().getId(), 0);
                if (rootCount <= 0) {
                    continue;
                }

                Map<Long, Integer> leafCapacities = bucket.leaves().stream()
                        .filter(leaf -> leaf.getAvailable(type) > 0)
                        .collect(Collectors.toMap(
                                leaf -> leaf.category().getId(),
                                leaf -> leaf.getAvailable(type),
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));
                Map<Long, Integer> chapterAllocation = allocateWeightedCounts(rootCount, leafCapacities, leafCapacities, false);

                for (ExamLeaf leaf : bucket.leaves()) {
                    int chapterCount = chapterAllocation.getOrDefault(leaf.category().getId(), 0);
                    if (chapterCount <= 0) {
                        continue;
                    }

                    List<Question> candidates = new ArrayList<>(leaf.questions(type));
                    Collections.shuffle(candidates, ThreadLocalRandom.current());
                    int take = Math.min(chapterCount, candidates.size());
                    selectedQuestions.addAll(candidates.subList(0, take));

                    sectionAccumulatorMap
                            .computeIfAbsent(bucket.category().getId(), key -> new SectionAccumulator(bucket.category()))
                            .addChapter(leaf.category(), take);
                }
            }
        }

        Collections.shuffle(selectedQuestions, ThreadLocalRandom.current());

        if (selectedQuestions.size() < actualTotal) {
            List<Question> remainingPool = new ArrayList<>(questionPool);
            remainingPool.removeIf(question -> selectedQuestions.stream().anyMatch(item -> item.getId().equals(question.getId())));
            Collections.shuffle(remainingPool, ThreadLocalRandom.current());
            remainingPool.stream()
                    .limit(actualTotal - selectedQuestions.size())
                    .forEach(selectedQuestions::add);
        }

        List<MockExamPaperResponse.MockExamSection> sections = sectionAccumulatorMap.values().stream()
                .map(SectionAccumulator::toSection)
                .toList();

        return new MockExamPaperResponse(
                requested,
                selectedQuestions.size(),
                availableQuestions,
                Math.max(30, selectedQuestions.size() * 2),
                selectedQuestions,
                sections
        );
    }

    private void normalizeQuestion(Question question) {
        if (question == null) {
            throw new BusinessException("题目信息不能为空");
        }
        if (!StringUtils.hasText(question.getContent())) {
            throw new BusinessException("题目内容不能为空");
        }
        if (question.getCategoryId() == null) {
            throw new BusinessException("请选择所属分类");
        }

        question.setContent(question.getContent().trim());
        question.setType(question.getType() == null ? 1 : question.getType());
        question.setDifficulty(question.getDifficulty() == null ? 1 : question.getDifficulty());
        question.setStatus(question.getStatus() == null ? 1 : question.getStatus());
        question.setSourceType(normalizeSourceType(question.getSourceType(), question.getSource()));
        question.setTags(trimToNull(question.getTags()));
        question.setSource(trimToNull(question.getSource()));
        question.setOptionA(trimToNull(question.getOptionA()));
        question.setOptionB(trimToNull(question.getOptionB()));
        question.setOptionC(trimToNull(question.getOptionC()));
        question.setOptionD(trimToNull(question.getOptionD()));
        question.setAnalysis(trimToNull(question.getAnalysis()));
        question.setSolutionStrategy(trimToNull(question.getSolutionStrategy()));

        validateQuestionType(question.getType());
        validateDifficulty(question.getDifficulty());
        validateSourceType(question.getSourceType());
        validateCategory(question.getCategoryId());
        validateOptions(question);

        String normalizedCorrectAnswer = normalizeAnswer(question.getType(), question.getCorrectAnswer());
        if (!StringUtils.hasText(normalizedCorrectAnswer)) {
            throw new BusinessException("正确答案不能为空");
        }
        validateCorrectAnswer(question.getType(), normalizedCorrectAnswer);
        question.setCorrectAnswer(normalizedCorrectAnswer);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeAnswer(Integer type, String answer) {
        if (!StringUtils.hasText(answer)) {
            return "";
        }

        if (type != null && type == 5) {
            return Arrays.stream(answer.toUpperCase().split("[,，/\\s]+"))
                    .map(String::trim)
                    .filter(token -> token.matches("[A-Z]"))
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining(","));
        }

        return answer.trim().toUpperCase();
    }

    private void validateQuestionType(Integer type) {
        if (type == null || !List.of(1, 2, 4, 5).contains(type)) {
            throw new BusinessException("当前仅支持单选、多选、填空和简答题");
        }
    }

    private void validateDifficulty(Integer difficulty) {
        if (difficulty == null || difficulty < 1 || difficulty > 3) {
            throw new BusinessException("难度必须在 1 到 3 之间");
        }
    }

    private void validateSourceType(Integer sourceType) {
        if (sourceType == null || (sourceType != 1 && sourceType != 2)) {
            throw new BusinessException("题目来源类型只能是真题或模拟题");
        }
    }

    private void validateCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException("所选分类不存在");
        }
    }

    private void validateOptions(Question question) {
        if (question.getType() != 1 && question.getType() != 5) {
            return;
        }

        if (!StringUtils.hasText(question.getOptionA()) || !StringUtils.hasText(question.getOptionB())) {
            throw new BusinessException("选择题至少需要填写选项 A 和选项 B");
        }
    }

    private void validateCorrectAnswer(Integer type, String correctAnswer) {
        if (type == 1 && !correctAnswer.matches("[A-D]")) {
            throw new BusinessException("单选题答案必须是 A-D 中的一个选项");
        }

        if (type == 5 && !correctAnswer.matches("[A-D](,[A-D])+")) {
            throw new BusinessException("多选题答案必须使用 A,C 这样的格式，且至少包含两个选项");
        }
    }

    private Integer normalizeSourceType(Integer sourceType, String source) {
        if (sourceType != null && (sourceType == 1 || sourceType == 2)) {
            return sourceType;
        }

        String normalizedSource = source == null ? "" : source.trim().toLowerCase();
        if (
                normalizedSource.contains("模拟")
                        || normalizedSource.contains("mock")
                        || normalizedSource.contains("1000题")
                        || normalizedSource.contains("肖秀荣")
        ) {
            return 2;
        }
        return 1;
    }

    private static Map<Long, Integer> buildMockExamRootWeights() {
        Map<Long, Integer> weights = new LinkedHashMap<>();
        weights.put(1L, 24);
        weights.put(2L, 30);
        weights.put(3L, 14);
        weights.put(4L, 16);
        weights.put(5L, 16);
        return Collections.unmodifiableMap(weights);
    }

    private Map<Integer, List<Question>> groupQuestionsByType(List<Question> questions) {
        return questions.stream().collect(Collectors.groupingBy(Question::getType, LinkedHashMap::new, Collectors.toList()));
    }

    private List<Category> listCategories(boolean activeOnly) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        if (activeOnly) {
            wrapper.eq(Category::getStatus, 1);
        }
        wrapper.orderByAsc(Category::getParentId).orderByAsc(Category::getSort).orderByAsc(Category::getId);
        return categoryMapper.selectList(wrapper);
    }

    private List<Long> resolveCategoryIds(Long categoryId, boolean activeOnly) {
        if (categoryId == null) {
            return List.of();
        }

        List<Category> categories = listCategories(activeOnly);
        Map<Long, List<Category>> childrenMap = buildChildrenMap(categories);
        if (categories.stream().noneMatch(category -> categoryId.equals(category.getId()))) {
            return List.of();
        }

        List<Long> categoryIds = new ArrayList<>();
        collectCategoryIds(categoryId, childrenMap, categoryIds);
        return categoryIds;
    }

    private Map<Long, List<Category>> buildChildrenMap(List<Category> categories) {
        return categories.stream()
                .filter(category -> category.getParentId() != null && category.getParentId() > 0)
                .collect(Collectors.groupingBy(Category::getParentId, LinkedHashMap::new, Collectors.toList()));
    }

    private void collectCategoryIds(Long categoryId, Map<Long, List<Category>> childrenMap, List<Long> categoryIds) {
        categoryIds.add(categoryId);
        for (Category child : childrenMap.getOrDefault(categoryId, List.of())) {
            collectCategoryIds(child.getId(), childrenMap, categoryIds);
        }
    }

    private boolean isRoot(Category category) {
        return category.getParentId() == null || category.getParentId() == 0;
    }

    private void clearQuestionCaches(Long questionId) {
        if (questionId != null) {
            cacheService.delete("question:" + questionId);
        }
        cacheService.deleteByPrefix("questions:category:");
    }

    private <T> Map<T, Integer> allocateWeightedCounts(
            int total,
            Map<T, Integer> capacities,
            Map<T, Integer> weights,
            boolean ensureOne
    ) {
        Map<T, Integer> result = new LinkedHashMap<>();
        Map<T, Integer> availableCapacities = capacities.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));

        if (total <= 0 || availableCapacities.isEmpty()) {
            return result;
        }

        int remaining = total;
        if (ensureOne && total >= availableCapacities.size()) {
            for (T key : availableCapacities.keySet()) {
                result.put(key, 1);
                remaining -= 1;
            }
        }

        List<AllocationRemainder> remainders = new ArrayList<>();
        double totalWeight = availableCapacities.keySet().stream()
                .mapToDouble(key -> Math.max(0, weights.getOrDefault(key, 1)))
                .sum();

        if (remaining > 0 && totalWeight > 0) {
            for (T key : availableCapacities.keySet()) {
                int baseAssigned = result.getOrDefault(key, 0);
                int capacity = Math.max(0, availableCapacities.getOrDefault(key, 0) - baseAssigned);
                if (capacity <= 0) {
                    continue;
                }

                double exact = remaining * weights.getOrDefault(key, 1) / totalWeight;
                int allocated = Math.min(capacity, (int) Math.floor(exact));
                if (allocated > 0) {
                    result.put(key, baseAssigned + allocated);
                }
                remainders.add(new AllocationRemainder(key, exact - allocated, capacity - allocated));
            }

            int assigned = result.values().stream().mapToInt(Integer::intValue).sum();
            int leftover = total - assigned;
            remainders.stream()
                    .sorted((left, right) -> Double.compare(right.remainder(), left.remainder()))
                    .forEach(item -> {
                        if (item.capacity() <= 0) {
                            return;
                        }
                        if (result.values().stream().mapToInt(Integer::intValue).sum() >= total) {
                            return;
                        }
                        result.put((T) item.key(), result.getOrDefault((T) item.key(), 0) + 1);
                    });

            if (leftover > 0 && result.values().stream().mapToInt(Integer::intValue).sum() < total) {
                for (T key : availableCapacities.keySet()) {
                    int assignedCount = result.getOrDefault(key, 0);
                    while (assignedCount < availableCapacities.getOrDefault(key, 0)
                            && result.values().stream().mapToInt(Integer::intValue).sum() < total) {
                        assignedCount += 1;
                        result.put(key, assignedCount);
                    }
                }
            }
        }

        return result;
    }

    private record ExamLeaf(Category category, Map<Integer, List<Question>> questionsByType) {
        private int getAvailable() {
            return questionsByType.values().stream().mapToInt(List::size).sum();
        }

        private int getAvailable(int type) {
            return questions(type).size();
        }

        private List<Question> questions(int type) {
            return questionsByType.getOrDefault(type, List.of());
        }
    }

    private record ExamBucket(Category category, List<ExamLeaf> leaves) {
        private int getAvailable() {
            return leaves.stream().mapToInt(ExamLeaf::getAvailable).sum();
        }

        private int getAvailable(int type) {
            return leaves.stream().mapToInt(leaf -> leaf.getAvailable(type)).sum();
        }
    }

    private record AllocationRemainder(Object key, double remainder, int capacity) {
    }

    private static final class SectionAccumulator {
        private final Category root;
        private final Map<Long, ChapterAccumulator> chapters = new LinkedHashMap<>();

        private SectionAccumulator(Category root) {
            this.root = root;
        }

        private void addChapter(Category category, int questionCount) {
            chapters.computeIfAbsent(category.getId(), key -> new ChapterAccumulator(category)).add(questionCount);
        }

        private MockExamPaperResponse.MockExamSection toSection() {
            List<MockExamPaperResponse.MockExamChapter> chapterItems = chapters.values().stream()
                    .map(ChapterAccumulator::toChapter)
                    .toList();
            int total = chapterItems.stream().mapToInt(MockExamPaperResponse.MockExamChapter::getQuestionCount).sum();
            return new MockExamPaperResponse.MockExamSection(root.getId(), root.getName(), total, chapterItems);
        }
    }

    private static final class ChapterAccumulator {
        private final Category category;
        private int questionCount;

        private ChapterAccumulator(Category category) {
            this.category = category;
        }

        private void add(int count) {
            this.questionCount += count;
        }

        private MockExamPaperResponse.MockExamChapter toChapter() {
            return new MockExamPaperResponse.MockExamChapter(category.getId(), category.getName(), questionCount);
        }
    }
}
