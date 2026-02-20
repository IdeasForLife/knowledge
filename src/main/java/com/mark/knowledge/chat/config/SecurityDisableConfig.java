package com.mark.knowledge.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.boot.web.servlet.ServletContextInitializer;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;

import java.util.EnumSet;

/**
 * 完全禁用 Spring Security 的 Web 安全功能
 *
 * 我们只使用 PasswordEncoder，其他全部禁用
 *
 * @author mark
 */
@Configuration
@EnableWebSecurity
public class SecurityDisableConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // 完全禁用CSRF
            .cors(cors -> cors.disable())  // 禁用CORS
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()  // 允许所有请求，不做任何拦截
            )
            .headers(headers -> headers.disable())  // 禁用所有安全头
            .securityContext(security -> security.disable())  // 禁用安全上下文
            .sessionManagement(session -> session.disable());  // 禁用Spring Security的Session管理

        return http.build();
    }

    /**
     * 配置ServletContext，确保Session正常工作
     */
    @Bean
    public ServletContextInitializer clearSessionInitializer() {
        return servletContext -> {
            // 配置Session Cookie
            SessionCookieConfig sessionCookieConfig = servletContext.getSessionCookieConfig();
            sessionCookieConfig.setHttpOnly(true);
            sessionCookieConfig.setSecure(false);  // 开发环境用false
            sessionCookieConfig.setMaxAge(3600);  // 1小时
            sessionCookieConfig.setPath("/");

            // 设置Session跟踪模式为COOKIE
            servletContext.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
        };
    }
}
