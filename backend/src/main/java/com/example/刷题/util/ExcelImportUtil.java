package com.example.刷题.util;

import com.example.刷题.entity.Question;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExcelImportUtil {

    public static List<Question> importQuestions(MultipartFile file) {
        List<Question> questions = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = createWorkbook(file.getOriginalFilename(), inputStream);
            Sheet sheet = workbook.getSheetAt(0);

            int firstRow = sheet.getFirstRowNum() + 1;
            int lastRow = sheet.getLastRowNum();
            for (int i = firstRow; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Question question = parseQuestion(row);
                if (question != null) {
                    questions.add(question);
                }
            }

            workbook.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to import Excel: " + e.getMessage(), e);
        }

        return questions;
    }

    private static Workbook createWorkbook(String filename, InputStream inputStream) throws Exception {
        if (filename == null) {
            throw new IllegalArgumentException("File name is required");
        }
        if (filename.toLowerCase().endsWith(".xlsx")) {
            return new XSSFWorkbook(inputStream);
        }
        if (filename.toLowerCase().endsWith(".xls")) {
            return new HSSFWorkbook(inputStream);
        }
        throw new IllegalArgumentException("Only .xlsx and .xls files are supported");
    }

    private static Question parseQuestion(Row row) {
        String content = getCellStringValue(row.getCell(0));
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        Question question = new Question();
        question.setContent(content.trim());

        boolean extendedFormat = row.getLastCellNum() >= 10;
        question.setType(parseType(row.getCell(1)));
        question.setDifficulty(parseDifficulty(row.getCell(2)));
        question.setTags(getCellStringValue(row.getCell(3)));

        if (row.getLastCellNum() >= 15) {
            question.setSource(getCellStringValue(row.getCell(4)));
            question.setSourceType(parseSourceType(row.getCell(5), question.getSource()));
            question.setOptionA(getCellStringValue(row.getCell(6)));
            question.setOptionB(getCellStringValue(row.getCell(7)));
            question.setOptionC(getCellStringValue(row.getCell(8)));
            question.setOptionD(getCellStringValue(row.getCell(9)));
            question.setCorrectAnswer(getCellStringValue(row.getCell(10)));
            question.setAnalysis(getCellStringValue(row.getCell(11)));
            question.setSolutionStrategy(getCellStringValue(row.getCell(12)));
            question.setCategoryId(getCellLongValue(row.getCell(13)));
            Integer status = getCellIntValue(row.getCell(14));
            question.setStatus(status != null ? status : 1);
        } else if (extendedFormat) {
            question.setSource(getCellStringValue(row.getCell(4)));
            question.setSourceType(inferSourceType(question.getSource()));
            question.setOptionA(getCellStringValue(row.getCell(5)));
            question.setOptionB(getCellStringValue(row.getCell(6)));
            question.setOptionC(getCellStringValue(row.getCell(7)));
            question.setOptionD(getCellStringValue(row.getCell(8)));
            question.setCorrectAnswer(getCellStringValue(row.getCell(9)));
            question.setAnalysis(getCellStringValue(row.getCell(10)));
            question.setSolutionStrategy(getCellStringValue(row.getCell(11)));
            question.setCategoryId(getCellLongValue(row.getCell(12)));
            Integer status = getCellIntValue(row.getCell(13));
            question.setStatus(status != null ? status : 1);
        } else {
            question.setCorrectAnswer(getCellStringValue(row.getCell(4)));
            question.setAnalysis(getCellStringValue(row.getCell(5)));
            question.setCategoryId(getCellLongValue(row.getCell(6)));
            question.setSourceType(1);
            question.setStatus(1);
        }

        return question;
    }

    private static Integer parseType(Cell cell) {
        Integer numeric = getCellIntValue(cell);
        if (numeric != null) {
            return numeric;
        }

        String label = getCellStringValue(cell);
        if (label == null) {
            return 1;
        }

        String normalized = label.trim().toLowerCase();
        return switch (normalized) {
            case "1", "单选", "单选题", "single", "single-choice" -> 1;
            case "2", "填空", "填空题", "blank" -> 2;
            case "4", "简答", "简答题", "essay", "short-answer" -> 4;
            case "5", "多选", "多选题", "multiple", "multiple-choice" -> 5;
            default -> 1;
        };
    }

    private static Integer parseDifficulty(Cell cell) {
        Integer numeric = getCellIntValue(cell);
        if (numeric != null) {
            return numeric;
        }

        String label = getCellStringValue(cell);
        if (label == null) {
            return 1;
        }

        String normalized = label.trim().toLowerCase();
        return switch (normalized) {
            case "1", "基础", "简单", "basic", "easy" -> 1;
            case "2", "提高", "中等", "medium" -> 2;
            case "3", "冲刺", "困难", "hard" -> 3;
            default -> 1;
        };
    }

    private static Integer parseSourceType(Cell cell, String source) {
        Integer numeric = getCellIntValue(cell);
        if (numeric != null && (numeric == 1 || numeric == 2)) {
            return numeric;
        }

        String label = getCellStringValue(cell);
        if (label == null || label.trim().isEmpty()) {
            return inferSourceType(source);
        }

        String normalized = label.trim().toLowerCase();
        return switch (normalized) {
            case "1", "真题", "真题卷", "real", "real-exam", "past-paper", "exam" -> 1;
            case "2", "模拟题", "模拟卷", "mock", "mock-exam" -> 2;
            default -> inferSourceType(source);
        };
    }

    private static Integer inferSourceType(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase();
        if (
                normalized.contains("模拟")
                        || normalized.contains("mock")
                        || normalized.contains("1000题")
                        || normalized.contains("肖秀荣")
        ) {
            return 2;
        }
        return 1;
    }

    private static String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double numericValue = cell.getNumericCellValue();
                if (numericValue == Math.rint(numericValue)) {
                    yield String.valueOf((long) numericValue);
                }
                yield String.valueOf(numericValue);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception ex) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    private static Integer getCellIntValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Integer.parseInt(cell.getStringCellValue().trim());
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private static Long getCellLongValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case NUMERIC -> (long) cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Long.parseLong(cell.getStringCellValue().trim());
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
