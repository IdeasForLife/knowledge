package com.mark.knowledge.k8s.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Kubernetes 配置类
 *
 * @author mark
 */
@Data
@Component
@ConfigurationProperties(prefix = "kubernetes")
public class KubernetesConfig {

    /**
     * K8s API 地址（通过 kubectl proxy）
     * 例如: http://localhost:8001
     */
    private String apiUrl = "http://localhost:8001";

    /**
     * API 版本
     */
    private String apiVersion = "api/v1";

    /**
     * 默认命名空间
     */
    private String defaultNamespace = "default";

    /**
     * 读取超时时间（秒）
     */
    private int readTimeout = 30;
}
