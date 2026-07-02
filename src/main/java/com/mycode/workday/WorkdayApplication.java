package com.mycode.workday;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Spring Boot + JavaFX 启动入口。
 * <p>
 * JavaFX 的 {@link Application} 负责窗口生命周期，
 * Spring Boot 负责依赖注入与服务管理。
 */
public class WorkdayApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        // 启动 Spring 容器，关闭 headless 模式以支持 GUI
        // 注意：传入 SpringConfig（@SpringBootApplication）而非本类，
        // 否则不会触发组件扫描，导致 Controller/Service 等 Bean 找不到
        context = new SpringApplicationBuilder(SpringConfig.class)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main.fxml"));
        // 让 Spring 创建 Controller，从而支持 @Autowired 注入
        loader.setControllerFactory(context::getBean);

        Parent root = loader.load();

        Scene scene = new Scene(root, 860, 600);
        scene.getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm());

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
