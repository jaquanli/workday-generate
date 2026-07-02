package com.mycode.workday.controller;

import com.mycode.workday.model.Workday;
import com.mycode.workday.service.ExcelService;
import com.mycode.workday.service.WorkdayGenerateService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.util.Duration;
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

    @FXML private TextField yearField;
    @FXML private TextField startIdField;
    @FXML private Button btnGenerate;
    @FXML private Button btnExport;
    @FXML private Button btnClear;
    @FXML private TableView<Workday> tableView;
    @FXML private TableColumn<Workday, Number> colId;
    @FXML private TableColumn<Workday, LocalDate> colDate;
    @FXML private TableColumn<Workday, Number> colMonth;
    @FXML private TableColumn<Workday, Number> colYear;
    @FXML private Label statusLabel;
    @FXML private Label countLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    private final ObservableList<Workday> dataList = FXCollections.observableArrayList();
    private final ExcelService excelService;
    private final WorkdayGenerateService generateService;

    public MainController(ExcelService excelService, WorkdayGenerateService generateService) {
        this.excelService = excelService;
        this.generateService = generateService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colMonth.setCellValueFactory(new PropertyValueFactory<>("month"));
        colYear.setCellValueFactory(new PropertyValueFactory<>("year"));

        // 日期列格式化
        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : fmt.format(item));
            }
        });

        tableView.setItems(dataList);
        yearField.setText(String.valueOf(LocalDate.now().getYear()));
        startIdField.setText("1");
        updateCount();
    }

    /** 生成工作日（调用节假日接口）。 */
    @FXML
    private void onGenerate() {
        int year = parseInt(yearField.getText(), -1);
        if (year < 0) {
            showError("年份格式错误", "请输入有效的年份，例如 2026");
            return;
        }
        long startId = parseLong(startIdField.getText(), 1L);
        if (startId < 0) {
            showError("起始 Id 格式错误", "请输入有效的整数，例如 1 或 1001");
            return;
        }

        btnGenerate.setDisable(true);
        statusLabel.setText("正在生成 " + year + " 年工作日，请稍候...");
        showProgress(true);
        updateProgress(0, 1);

        asyncRun(() -> {
            var workdays = generateService.generate(year,
                    (done, total) -> runFx(() -> updateProgress(done, total)));
            // 按起始 Id 自增
            long id = startId;
            for (var wd : workdays) {
                wd.setId(id++);
            }
            runFx(() -> {
                dataList.setAll(workdays);
                updateCount();
                statusLabel.setText("生成完成：" + year + " 年共 " + workdays.size() + " 个工作日");
                btnGenerate.setDisable(false);
                hideProgressDelayed();
            });
        }, "生成", () -> runFx(() -> {
            btnGenerate.setDisable(false);
            showProgress(false);
        }));
    }

    /** 导出 Excel。 */
    @FXML
    private void onExport() {
        if (dataList.isEmpty()) {
            statusLabel.setText("没有数据可导出");
            return;
        }
        var chooser = new FileChooser();
        chooser.setTitle("保存 Excel 文件");
        chooser.setInitialFileName("workday_" + LocalDate.now().getYear() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel 文件", "*.xlsx"));
        File file = chooser.showSaveDialog(tableView.getScene().getWindow());
        if (file == null) {
            return;
        }

        statusLabel.setText("正在导出到: " + file.getName());
        asyncRun(() -> {
            excelService.exportExcel(dataList, file.getAbsolutePath());
            runFx(() -> statusLabel.setText("导出成功：" + file.getAbsolutePath()
                    + "，共 " + dataList.size() + " 条记录"));
        }, "导出", null);
    }

    /** 清空表格数据。 */
    @FXML
    private void onClear() {
        dataList.clear();
        updateCount();
        statusLabel.setText("已清空");
    }

    // ==================== 私有辅助 ====================

    /** 可抛受检异常的任务，供 {@link #asyncRun} 在后台线程执行。 */
    @FunctionalInterface
    private interface Task {
        void run() throws Exception;
    }

    /**
     * 在后台线程执行任务；失败则弹窗并在 UI 线程执行 failure。
     */
    private void asyncRun(Task task, String opName, Runnable failure) {
        new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                runFx(() -> {
                    statusLabel.setText(opName + "失败：" + e.getMessage());
                    if (failure != null) {
                        failure.run();
                    }
                    showError(opName + "失败", e.getMessage());
                });
            }
        }).start();
    }

    /** 把动作切回 JavaFX UI 线程执行。 */
    private void runFx(Runnable action) {
        Platform.runLater(action);
    }

    /** 500ms 后隐藏进度条。 */
    private void hideProgressDelayed() {
        var pause = new PauseTransition(Duration.millis(500));
        pause.setOnFinished(e -> showProgress(false));
        pause.play();
    }

    private void showProgress(boolean visible) {
        progressBar.setVisible(visible);
        progressLabel.setVisible(visible);
    }

    private void updateProgress(int done, int total) {
        progressBar.setProgress(total <= 0 ? 0 : Math.min(1.0, done / (double) total));
        progressLabel.setText(done + "/" + total);
    }

    private void updateCount() {
        countLabel.setText("共 " + dataList.size() + " 条");
    }

    private void showError(String title, String message) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** 解析整数，失败返回 defaultValue。 */
    private static int parseInt(String text, int defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 解析 long，失败返回 defaultValue。 */
    private static long parseLong(String text, long defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
