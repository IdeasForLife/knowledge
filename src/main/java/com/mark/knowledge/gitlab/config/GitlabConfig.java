package com.mark.knowledge.gitlab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * GitLab配置类
 *
 * @author mark
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gitlab")
public class GitlabConfig {

    /**
     * GitLab服务器URL
     */
    private String url;

    /**
     * 访问令牌
     */
    private String token;

    /**
     * Webhook 密钥（用于验证 GitLab Webhook 请求）
     */
    private String webhookToken;

    /**
     * API版本
     */
    private String apiVersion = "api/v4";
}
