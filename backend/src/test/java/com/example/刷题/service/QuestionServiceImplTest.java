package com.example.刷题.service;

import com.example.刷题.entity.Question;
import com.example.刷题.mapper.QuestionMapper;
import com.example.刷题.service.impl.QuestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Mock
    private QuestionMapper questionMapper;

    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new QuestionServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", questionMapper);
    }

    @Test
    void getByIdsReturnsEmptyWhenInputIsNullOrEmpty() {
        assertTrue(service.getByIds(null).isEmpty());
        assertTrue(service.getByIds(List.of()).isEmpty());
    }

    @Test
    void getByIdsPreservesRequestedUniqueOrder() {
        Question question1 = buildQuestion(1L);
        Question question2 = buildQuestion(2L);
        Question question3 = buildQuestion(3L);
        AtomicReference<List<Long>> requestedIds = new AtomicReference<>();

        when(questionMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            requestedIds.set(List.copyOf(invocation.getArgument(0)));
            return List.of(question2, question3, question1);
        });

        List<Question> result = service.getByIds(Arrays.asList(3L, 1L, 3L, null, 2L));

        assertEquals(List.of(3L, 1L, 2L), result.stream().map(Question::getId).toList());
        assertEquals(List.of(3L, 1L, 2L), requestedIds.get());
        verify(questionMapper).selectBatchIds(anyCollection());
    }

    private Question buildQuestion(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setContent("question-" + id);
        return question;
    }
}
