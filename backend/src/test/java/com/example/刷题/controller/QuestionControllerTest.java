package com.example.刷题.controller;

import com.example.刷题.common.Result;
import com.example.刷题.entity.Question;
import com.example.刷题.exception.BusinessException;
import com.example.刷题.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private QuestionService questionService;

    private QuestionController controller;

    @BeforeEach
    void setUp() {
        controller = new QuestionController();
        ReflectionTestUtils.setField(controller, "questionService", questionService);
    }

    @Test
    void getQuestionsByIdsParsesDistinctIds() {
        Question question3 = buildQuestion(3L);
        Question question1 = buildQuestion(1L);
        Question question2 = buildQuestion(2L);
        when(questionService.getByIds(List.of(3L, 1L, 2L))).thenReturn(List.of(question3, question1, question2));

        Result<List<Question>> result = controller.getQuestionsByIds("3,1,3,2");

        assertEquals(200, result.getCode());
        assertEquals(List.of(3L, 1L, 2L), result.getData().stream().map(Question::getId).toList());
        verify(questionService).getByIds(List.of(3L, 1L, 2L));
    }

    @Test
    void getQuestionsByIdsRejectsInvalidIdFormat() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.getQuestionsByIds("1,a,2")
        );

        assertEquals(400, exception.getCode());
        assertEquals("题目ID格式不正确", exception.getMessage());
    }

    private Question buildQuestion(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setContent("question-" + id);
        return question;
    }
}
