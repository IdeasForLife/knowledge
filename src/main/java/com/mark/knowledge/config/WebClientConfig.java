package com.mark.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient配置类
 *
 * 配置用于REST API调用的WebClient
 * - 设置内存缓冲区大小
 * - 设置超时时间
 */
@Configuration
public class WebClientConfig {

    /**
     * 创建WebClient构建器Bean
     *
     * @return WebClient构建器实例
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024));  // 10MB缓冲区
    }
}
