package com.mycode.workday.service;

import com.mycode.workday.model.Workday;
import org.apache.fesod.sheet.FesodSheet;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Excel 导出服务，基于 Apache Fesod。
 * <p>
 * 导出列格式由 {@link Workday} 上的 {@code @ExcelProperty} 注解决定：
 * {@code Id | 序号 | 日期 | 月份 | 年份}。
 */
@Service
public class ExcelService {

    /**
     * 将工作日列表导出到 Excel 文件（.xlsx）。
     */
    public void exportExcel(List<Workday> workdays, String filePath) throws Exception {
        try (var writer = FesodSheet.write(filePath, Workday.class).build()) {
            writer.write(workdays, FesodSheet.writerSheet("工作日").build());
        }
    }
}
