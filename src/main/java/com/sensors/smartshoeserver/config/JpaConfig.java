package com.sensors.smartshoeserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA配置类
 * 启用JPA审计功能（自动填充创建时间和更新时间）
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}