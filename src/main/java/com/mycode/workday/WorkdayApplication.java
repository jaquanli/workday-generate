package com.mycode.workday;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX + Spring Boot 启动入口（合并版）。
 * <p>
 * 同时承担三个职责：
 * <ul>
 *   <li>JavaFX {@link Application}：管理窗口生命周期</li>
 *   <li>{@code @SpringBootApplication}：触发自动配置与组件扫描</li>
 *   <li>Spring 容器持有者：init 启动、stop 关闭</li>
 * </ul>
 * 由独立的 {@link Launcher} 间接启动（规避 JavaFX 运行时组件错误）。
 */
@SpringBootApplication
public class WorkdayApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        // headless(false) 由 application.yml 配置；传入自身触发组件扫描
        context = new SpringApplicationBuilder(WorkdayApplication.class)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(context::getBean);

        Parent root = loader.load();

        Scene scene = new Scene(root, 860, 600);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        primaryStage.setTitle("工作日 Excel 工具");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(480);
        primaryStage.show();
    }

    @Override
    public void stop() {
        context.close();
    }
}
