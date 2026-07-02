package com.mycode.workday.controller;

import com.mycode.workday.model.Workday;
import com.mycode.workday.service.ExcelService;
import com.mycode.workday.service.WorkdayGenerateService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 主界面控制器。由 Spring 创建并注入服务依赖。
 */
@Component
public class MainController implements Initializable {

    @FXML
    private TextField yearField;

    /** Id 自增的起始值，由用户填写 */
    @FXML
    private TextField startIdField;

    @FXML
    private Button btnGenerate;

    @FXML
    private Button btnExport;

    @FXML
    private Button btnClear;

    @FXML
    private TableView<Workday> tableView;

    @FXML
    private TableColumn<Workday, Number> colId;

    @FXML
    private TableColumn<Workday, Integer> colNo;

    @FXML
    private TableColumn<Workday, LocalDate> colDate;

    @FXML
    private TableColumn<Workday, Integer> colMonth;

    @FXML
    private TableColumn<Workday, Integer> colYear;

    @FXML
    private Label statusLabel;

    @FXML
    private Label countLabel;

    /** 底部状态栏的进度条，生成工作日时显示 */
    @FXML
    private ProgressBar progressBar;

    /** 进度条右侧的文字（如 35/104） */
    @FXML
    private Label progressLabel;

    private final ObservableList<Workday> dataList = FXCollections.observableArrayList();

    private final ExcelService excelService;
    private final WorkdayGenerateService generateService;

    @Autowired
    public MainController(ExcelService excelService,
                          WorkdayGenerateService generateService) {
        this.excelService = excelService;
        this.generateService = generateService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 绑定表格列
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNo.setCellValueFactory(new PropertyValueFactory<>("no"));
        colMonth.setCellValueFactory(new PropertyValueFactory<>("month"));
        colYear.setCellValueFactory(new PropertyValueFactory<>("year"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        // 日期列格式化显示
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        colDate.setCellFactory(column -> new TableCell<Workday, LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : fmt.format(item));
            }
        });

        // 居中显示
        colId.setStyle("-fx-alignment: CENTER;");
        colNo.setStyle("-fx-alignment: CENTER;");
        colDate.setStyle("-fx-alignment: CENTER;");
        colMonth.setStyle("-fx-alignment: CENTER;");
        colYear.setStyle("-fx-alignment: CENTER;");

        tableView.setItems(dataList);
        yearField.setText(String.valueOf(LocalDate.now().getYear()));
        // 起始 Id 默认从 1 开始
        startIdField.setText("1");

        updateCount();
    }

    /**
     * 生成工作日（调用节假日接口）。
     */
    @FXML
    private void onGenerate() {
        int year = parseYear();
        if (year == 0) return;
        long startId = parseStartId();
        if (startId < 0) return;

        btnGenerate.setDisable(true);
        statusLabel.setText("正在生成 " + year + " 年工作日，请稍候...");

        // 周末日总数 = 需要查询补班的请求数，作为进度条分母
        int totalWeekends = countWeekends(year);
        showProgress(true);
        updateProgress(0, totalWeekends);

        // 放到后台线程执行，避免阻塞 UI
        new Thread(() -> {
            try {
                List<Workday> workdays = generateService.generate(year, done ->
                        // 回调在工作线程触发，需切回 JavaFX 线程更新进度条
                        Platform.runLater(() -> updateProgress(done, totalWeekends)));
                long currentId = startId;
                int no = 1;
                for (Workday wd : workdays) {
                    wd.setId(currentId++);
                    wd.setNo(no++);
                }
                Platform.runLater(() -> {
                    dataList.setAll(workdays);
                    updateCount();
                    statusLabel.setText("生成完成：" + year + " 年共 " + workdays.size() + " 个工作日");
                    btnGenerate.setDisable(false);
                    updateProgress(totalWeekends, totalWeekends);
                    // 稍作停留后隐藏进度条
                    new Thread(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> showProgress(false));
                    }).start();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("生成失败：" + e.getMessage());
                    btnGenerate.setDisable(false);
                    showProgress(false);
                    showError("生成失败", e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 导出 Excel。
     */
    @FXML
    private void onExport() {
        if (dataList.isEmpty()) {
            statusLabel.setText("没有数据可导出");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存 Excel 文件");
        String defaultName = "workday_" + LocalDate.now().getYear() + ".xlsx";
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel 文件", "*.xlsx")
        );
        File file = chooser.showSaveDialog(tableView.getScene().getWindow());
        if (file == null) return;

        statusLabel.setText("正在导出到: " + file.getName());
        new Thread(() -> {
            try {
                excelService.exportExcel(dataList, file.getAbsolutePath());
                Platform.runLater(() -> {
                    statusLabel.setText("导出成功：" + file.getAbsolutePath()
                            + "，共 " + dataList.size() + " 条记录");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("导出失败：" + e.getMessage());
                    showError("导出失败", e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 清空表格数据。
     */
    @FXML
    private void onClear() {
        dataList.clear();
        updateCount();
        statusLabel.setText("已清空");
    }

    private int parseYear() {
        try {
            return Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            showError("年份格式错误", "请输入有效的年份，例如 2026");
            return 0;
        }
    }

    /**
     * 统计某年周末日数量（周六+周日），作为生成进度条的分母。
     */
    private int countWeekends(int year) {
        int count = 0;
        for (int month = 1; month <= 12; month++) {
            int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
            for (int day = 1; day <= daysInMonth; day++) {
                java.time.DayOfWeek dow = java.time.LocalDate.of(year, month, day).getDayOfWeek();
                if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 显示/隐藏底部进度条及文字 */
    private void showProgress(boolean visible) {
        progressBar.setVisible(visible);
        progressLabel.setVisible(visible);
    }

    /** 更新进度条比例与文字（done/total） */
    private void updateProgress(int done, int total) {
        if (total <= 0) {
            progressBar.setProgress(0);
        } else {
            progressBar.setProgress(Math.min(1.0, done / (double) total));
        }
        progressLabel.setText(done + "/" + total);
    }

    /**
     * 解析起始 Id。允许为空（按默认 1 处理）。解析失败时返回 -1 表示出错。
     */
    private long parseStartId() {
        String text = startIdField.getText();
        if (text == null || text.trim().isEmpty()) {
            return 1L;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            showError("起始 Id 格式错误", "请输入有效的整数，例如 1 或 1001");
            return -1L;
        }
    }

    private void updateCount() {
        countLabel.setText("共 " + dataList.size() + " 条");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
