package com.mycode.workday.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDate;

/**
 * 工作日模型，使用 JavaFX Property 以便 TableView 绑定。
 * 对应原项目 Workday 实体：序号 / 日期(sdate) / 月份(smonth) / 年份(syear)。
 * <p>
 * {@code id} 为用户指定的起始 Id 自增字段（{@code long}，足以支撑较大起始值）。
 */
public class Workday {

    private final SimpleLongProperty id = new SimpleLongProperty();
    private final SimpleIntegerProperty no = new SimpleIntegerProperty();
    private final SimpleObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
    private final SimpleIntegerProperty month = new SimpleIntegerProperty();
    private final SimpleIntegerProperty year = new SimpleIntegerProperty();

    public Workday() {
    }

    public Workday(long id, int no, LocalDate date, int month, int year) {
        setId(id);
        setNo(no);
        setDate(date);
        setMonth(month);
        setYear(year);
    }

    public long getId() {
        return id.get();
    }

    public void setId(long id) {
        this.id.set(id);
    }

    public SimpleLongProperty idProperty() {
        return id;
    }

    public int getNo() {
        return no.get();
    }

    public void setNo(int no) {
        this.no.set(no);
    }

    public SimpleIntegerProperty noProperty() {
        return no;
    }

    public LocalDate getDate() {
        return date.get();
    }

    public void setDate(LocalDate date) {
        this.date.set(date);
    }

    public SimpleObjectProperty<LocalDate> dateProperty() {
        return date;
    }

    public int getMonth() {
        return month.get();
    }

    public void setMonth(int month) {
        this.month.set(month);
    }

    public SimpleIntegerProperty monthProperty() {
        return month;
    }

    public int getYear() {
        return year.get();
    }

    public void setYear(int year) {
        this.year.set(year);
    }

    public SimpleIntegerProperty yearProperty() {
        return year;
    }
}
