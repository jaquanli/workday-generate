# workday-excel

Spring Boot 4 + Apache Fesod + JavaFX 桌面应用，实现工作日 Excel 的**生成 / 导出**功能。

## 功能

- **生成工作日**：输入年份，调用节假日接口自动计算全年工作日（复用原项目 `WorkHoliday` 逻辑）
- **导出 Excel**：将当前表格数据导出为 `.xlsx` 文件
- **清空**：一键清空表格

Excel 列格式：`Id | 日期 | 月份 | 年份`，对应原项目 `Workday` 实体。

- **Id**：由用户在「起始 Id」输入框指定起始值，表格中从该值开始逐行自增（如填 `1001`，则 1001、1002、1003 …）。生成时会以该起始值重新编号。导出时 Id 列位于第一列。

## 技术栈

| 组件 | 版本 |
|------|------|
| Spring Boot | 4.0.0 |
| Apache Fesod | 2.0.1-incubating |
| JavaFX | 21.0.2 |
| Java | 17+ |

## 运行

```bash
cd /Users/jaquanli/Developer/code/mycode/workday-excel
mvn spring-boot:run
```

## 打包

```bash
mvn clean package
java -jar target/workday-excel-1.0.0.jar
```

## 平台适配

`pom.xml` 中 `javafx.classifier` 默认为 `mac-aarch64`（Apple Silicon）。其他平台请修改：

| 平台 | classifier |
|------|------------|
| Apple Silicon (M1/M2/M3) | `mac-aarch64` |
| Intel Mac | `mac` |
| Linux x86_64 | `linux` |
| Linux ARM64 | `linux-aarch64` |
| Windows x86_64 | `win` |

## 项目结构

```
workday-excel/
├── pom.xml
└── src/main/
    ├── java/com/mycode/workday/
    │   ├── Launcher.java                # main 入口（不继承 Application，规避 JavaFX 运行时错误）
    │   ├── WorkdayApplication.java      # JavaFX Application + @SpringBootApplication
    │   ├── model/Workday.java           # 工作日模型
    │   ├── service/
    │   │   ├── ExcelService.java        # Fesod 导出
    │   │   └── WorkdayGenerateService.java  # 节假日API生成
    │   └── controller/MainController.java   # JavaFX 控制器
    └── resources/
        ├── application.yml
        ├── fxml/main.fxml               # 界面布局
        └── css/style.css                # 样式
```
