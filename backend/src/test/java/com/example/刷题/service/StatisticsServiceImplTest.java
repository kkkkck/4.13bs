package com.example.刷题.service;

import com.example.刷题.mapper.PracticeRecordMapper;
import com.example.刷题.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private PracticeRecordMapper practiceRecordMapper;

    private StatisticsServiceImpl statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsServiceImpl();
        ReflectionTestUtils.setField(statisticsService, "practiceRecordMapper", practiceRecordMapper);
    }

    @Test
    void getDailyCorrectRateFillsMissingDays() {
        LocalDate today = LocalDate.now();
        when(practiceRecordMapper.selectDailyCorrectRate(1L, 3)).thenReturn(List.of(
                Map.of(
                        "date", today.minusDays(2).toString(),
                        "correctCount", 3,
                        "totalCount", 5,
                        "correctRate", 60.0
                ),
                Map.of(
                        "date", today.toString(),
                        "correctCount", 8,
                        "totalCount", 10,
                        "correctRate", 80.0
                )
        ));

        List<Map<String, Object>> rows = statisticsService.getDailyCorrectRate(1L, 3);

        assertEquals(3, rows.size());
        assertEquals(today.minusDays(2).toString(), rows.get(0).get("date"));
        assertEquals(60.0, rows.get(0).get("correctRate"));
        assertEquals(today.minusDays(1).toString(), rows.get(1).get("date"));
        assertEquals(0, rows.get(1).get("totalCount"));
        assertEquals(today.toString(), rows.get(2).get("date"));
        assertEquals(80.0, rows.get(2).get("correctRate"));
    }
}
