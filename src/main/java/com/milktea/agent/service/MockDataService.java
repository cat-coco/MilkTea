package com.milktea.agent.service;

import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * 从 Excel 文件读取 mock 数据，供财报分析工作流使用。
 * 修改 resources/mockdata/finance-mock-data.xlsx 即可更新前端展示的数据。
 */
@Service
public class MockDataService {

    private static final String MOCK_FILE = "mockdata/finance-mock-data.xlsx";

    private final Map<String, SheetData> sheetDataMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        loadExcel();
    }

    public void loadExcel() {
        sheetDataMap.clear();
        try (InputStream is = new ClassPathResource(MOCK_FILE).getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                String sheetName = sheet.getSheetName();

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                List<String> headers = new ArrayList<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    headers.add(getCellString(headerRow.getCell(c)));
                }

                List<Map<String, String>> rows = new ArrayList<>();
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    Map<String, String> rowMap = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        rowMap.put(headers.get(c), getCellString(row.getCell(c)));
                    }
                    rows.add(rowMap);
                }

                sheetDataMap.put(sheetName, new SheetData(sheetName, headers, rows));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mock data from " + MOCK_FILE, e);
        }
    }

    public SheetData getSheet(String sheetName) {
        return sheetDataMap.get(sheetName);
    }

    public List<String> getSheetNames() {
        return new ArrayList<>(sheetDataMap.keySet());
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }

    public record SheetData(String name, List<String> headers, List<Map<String, String>> rows) {}
}
