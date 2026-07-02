package com.mycode.workday.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.format.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 工作日模型。
 * <p>
 * 普通 POJO，getter/setter 由 Lombok 生成。{@code @ExcelProperty} 标注导出列名，
 * 供 Fesod 直接反射写出，无需额外 DTO。TableView 经 {@code PropertyValueFactory}
 * 读取标准 getter，同样兼容。
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Workday {

    @ExcelProperty("Id")
    private long id;

    @ExcelProperty("日期")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime date;

    @ExcelProperty("月份")
    private int month;

    @ExcelProperty("年份")
    private int year;
}
