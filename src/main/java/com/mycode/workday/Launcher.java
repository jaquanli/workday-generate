package com.mycode.workday;

import javafx.application.Application;

/**
 * 程序启动入口。
 * <p>
 * 注意：这个类<b>不能</b>继承 {@link javafx.application.Application}。
 * <p>
 * 从 Java 11 起，JavaFX 不再内置在 JDK 中。如果 JVM 启动时发现主类（main class）
 * 直接继承了 {@code Application}，就会尝试用 JavaFX 内置启动器运行，从而抛出
 * "缺少 JavaFX 运行时组件" 的错误。因此需要用一个独立的不继承 Application 的类
 * 来持有 {@code main} 方法，再由它显式调用 {@link Application#launch(Class, String[])}。
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(WorkdayApplication.class, args);
    }
}
