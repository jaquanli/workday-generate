package com.mycode.workday;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 配置入口。
 * <p>
 * 必须独立于 {@link WorkdayApplication}（JavaFX 的 {@code Application} 子类）。
 * 因为 {@code WorkdayApplication} 继承自 {@code javafx.application.Application}，
 * 不应也不能同时承担 Spring 配置类的角色。这里提供 {@link SpringBootApplication}
 * 以触发自动配置与组件扫描（扫描 {@code com.mycode.workday} 下的
 * {@code @Component}/{@code @Service} 等 Bean）。
 */
@SpringBootApplication
public class SpringConfig {
}
