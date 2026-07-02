package com.mycode.workday.service;

import com.mycode.workday.model.Workday;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 导出服务，基于 Apache Fesod。
 * <p>
 * 导出列格式：Id | 序号 | 日期 | 月份 | 年份。
 * <p>
 * <b>设计说明</b>：{@link Workday} 使用 JavaFX Property（如 {@code SimpleLongProperty}），
 * Fesod 反射读取字段时拿到的是 Property 对象本身而非其内部值。为避免该问题，
 * 这里用一个仅含普通类型的 {@link WorkdayRow} 承载 {@code @ExcelProperty} 注解，
 * 导出前做一次轻量映射。
 */
@Service
public class ExcelService {

    /**
     * 将工作日列表导出到 Excel 文件（.xlsx）。
     *
     * @param workdays 工作日列表
     * @param filePath 输出 xlsx 路径
     */
    public void exportExcel(List<Workday> workdays, String filePath) throws Exception {
        List<WorkdayRow> rows = new ArrayList<>(workdays.size());
        for (Workday wd : workdays) {
            rows.add(new WorkdayRow(wd.getId(), wd.getNo(), wd.getDate(), wd.getMonth(), wd.getYear()));
        }

        try (ExcelWriter writer = FesodSheet.write(filePath, WorkdayRow.class).build()) {
            WriteSheet sheet = FesodSheet.writerSheet("工作日").build();
            writer.write(rows, sheet);
        }
    }

    /**
     * 导出行模型：普通 Java 类型 + {@link ExcelProperty} 注解，供 Fesod 反射写出。
     */
    public static class WorkdayRow {

        @ExcelProperty("Id")
        private long id;

        @ExcelProperty("序号")
        private int no;

        @ExcelProperty("日期")
        private LocalDate date;

        @ExcelProperty("月份")
        private int month;

        @ExcelProperty("年份")
        private int year;

        public WorkdayRow() {
        }

        public WorkdayRow(long id, int no, LocalDate date, int month, int year) {
            this.id = id;
            this.no = no;
            this.date = date;
            this.month = month;
            this.year = year;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public int getNo() {
            return no;
        }

        public void setNo(int no) {
            this.no = no;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public int getMonth() {
            return month;
        }

        public void setMonth(int month) {
            this.month = month;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }
    }
}
