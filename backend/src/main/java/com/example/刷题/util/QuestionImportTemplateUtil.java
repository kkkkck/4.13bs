package com.example.刷题.util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class QuestionImportTemplateUtil {

    private QuestionImportTemplateUtil() {
    }

    public static byte[] buildTemplate() {
        try (Workbook workbook = new HSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Questions");

            Row header = sheet.createRow(0);
            String[] headers = {
                    "content", "type", "difficulty", "tags", "source", "sourceType",
                    "optionA", "optionB", "optionC", "optionD",
                    "correctAnswer", "analysis", "solutionStrategy", "categoryId", "status"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 18 * 256);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("Which options belong to the core principles of dialectics?");
            sample.createCell(1).setCellValue("multiple-choice");
            sample.createCell(2).setCellValue("medium");
            sample.createCell(3).setCellValue("marxism,dialectics");
            sample.createCell(4).setCellValue("Codex template");
            sample.createCell(5).setCellValue("模拟题");
            sample.createCell(6).setCellValue("Universal connection");
            sample.createCell(7).setCellValue("Static isolation");
            sample.createCell(8).setCellValue("Contradictory movement");
            sample.createCell(9).setCellValue("Pure accidentalism");
            sample.createCell(10).setCellValue("A,C");
            sample.createCell(11).setCellValue("Dialectics stresses universal connection and development through contradictions.");
            sample.createCell(12).setCellValue("Eliminate options that deny motion or contradiction.");
            sample.createCell(13).setCellValue(1);
            sample.createCell(14).setCellValue(1);

            Row sampleSingle = sheet.createRow(2);
            sampleSingle.createCell(0).setCellValue("The principal contradiction in the new era is best described as:");
            sampleSingle.createCell(1).setCellValue("single-choice");
            sampleSingle.createCell(2).setCellValue("basic");
            sampleSingle.createCell(3).setCellValue("new era,principal contradiction");
            sampleSingle.createCell(4).setCellValue("Codex template");
            sampleSingle.createCell(5).setCellValue("真题");
            sampleSingle.createCell(6).setCellValue("The contradiction between advanced socialist system and backward social productivity");
            sampleSingle.createCell(7).setCellValue("The contradiction between unbalanced and inadequate development and the people's growing needs for a better life");
            sampleSingle.createCell(8).setCellValue("The contradiction between planned economy and market economy");
            sampleSingle.createCell(9).setCellValue("The contradiction between openness and development");
            sampleSingle.createCell(10).setCellValue("B");
            sampleSingle.createCell(11).setCellValue("Use the standard textbook wording of the principal contradiction in the new era.");
            sampleSingle.createCell(12).setCellValue("Lock onto the standard textbook wording first, then eliminate legacy formulations.");
            sampleSingle.createCell(13).setCellValue(2);
            sampleSingle.createCell(14).setCellValue(1);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to build import template", ex);
        }
    }
}
