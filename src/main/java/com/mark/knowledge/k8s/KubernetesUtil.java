package com.mark.knowledge.k8s;

import com.fasterxml.jackson.databind.JsonNode;
import com.mark.knowledge.k8s.config.KubernetesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Kubernetes 工具类 - 提供与 Kubernetes API 交互的基础方法
 *
 * 使用 JsonNode 解析响应，避免复杂的 DTO 定义
 *
 * @author mark
 */
@Component
public class KubernetesUtil {

    private static final Logger log = LoggerFactory.getLogger(KubernetesUtil.class);

    private final KubernetesConfig k8sConfig;
    private final WebClient webClient;

    public KubernetesUtil(KubernetesConfig k8sConfig) {
        this.k8sConfig = k8sConfig;
        this.webClient = WebClient.builder()
                .baseUrl(k8sConfig.getApiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ==================== Pod 相关 ====================

    /**
     * 获取指定命名空间的所有 Pod
     */
    public JsonNode getPods(String namespace) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取命名空间 {} 的所有 Pod", ns);

        return webClient.get()
                .uri("/{apiVersion}/namespaces/{namespace}/pods",
                        k8sConfig.getApiVersion(), ns)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * 获取所有命名空间的所有 Pod
     */
    public JsonNode getAllPods() {
        log.info("获取所有命名空间的 Pod");

        return webClient.get()
                .uri("/{apiVersion}/pods", k8sConfig.getApiVersion())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * 获取指定 Pod 的详情
     */
    public JsonNode getPod(String namespace, String podName) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取 Pod: {}/{}", ns, podName);

        return webClient.get()
                .uri("/{apiVersion}/namespaces/{namespace}/pods/{name}",
                        k8sConfig.getApiVersion(), ns, podName)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * 获取 Pod 日志
     */
    public String getPodLogs(String namespace, String podName, int tailLines) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取 Pod 日志: {}/{} (最近 {} 行)", ns, podName, tailLines);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{apiVersion}/namespaces/{namespace}/pods/{name}/log")
                        .queryParam("tailLines", tailLines)
                        .queryParam("timestamps", "true")
                        .build(k8sConfig.getApiVersion(), ns, podName))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 获取 Pod 日志（默认 100 行）
     */
    public String getPodLogs(String namespace, String podName) {
        return getPodLogs(namespace, podName, 100);
    }

    // ==================== Event 相关 ====================

    /**
     * 获取命名空间的事件
     */
    public JsonNode getEvents(String namespace) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取命名空间 {} 的事件", ns);

        return webClient.get()
                .uri("/{apiVersion}/namespaces/{namespace}/events",
                        k8sConfig.getApiVersion(), ns)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // ==================== Node 相关 ====================

    /**
     * 获取所有节点
     */
    public JsonNode getNodes() {
        log.info("获取所有节点");

        return webClient.get()
                .uri("/{apiVersion}/nodes", k8sConfig.getApiVersion())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * 获取节点详情
     */
    public JsonNode getNode(String nodeName) {
        log.info("获取节点详情: {}", nodeName);

        return webClient.get()
                .uri("/{apiVersion}/nodes/{name}",
                        k8sConfig.getApiVersion(), nodeName)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // ==================== Service 相关 ====================

    /**
     * 获取指定命名空间的所有 Service
     */
    public JsonNode getServices(String namespace) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取命名空间 {} 的所有 Service", ns);

        return webClient.get()
                .uri("/{apiVersion}/namespaces/{namespace}/services",
                        k8sConfig.getApiVersion(), ns)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * 获取 Service 详情
     */
    public JsonNode getService(String namespace, String serviceName) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取 Service: {}/{}", ns, serviceName);

        return webClient.get()
                .uri("/{apiVersion}/namespaces/{namespace}/services/{name}",
                        k8sConfig.getApiVersion(), ns, serviceName)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // ==================== Deployment 相关 ====================

    /**
     * 获取指定命名空间的所有 Deployment
     */
    public JsonNode getDeployments(String namespace) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取命名空间 {} 的所有 Deployment", ns);

        return webClient.get()
                .uri("/apis/apps/v1/namespaces/{namespace}/deployments", ns)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * 获取 Deployment 详情
     */
    public JsonNode getDeployment(String namespace, String deploymentName) {
        String ns = namespace != null ? namespace : k8sConfig.getDefaultNamespace();
        log.info("获取 Deployment: {}/{}", ns, deploymentName);

        return webClient.get()
                .uri("/apis/apps/v1/namespaces/{namespace}/deployments/{name}",
                        ns, deploymentName)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // ==================== Namespace 相关 ====================

    /**
     * 获取所有命名空间
     */
    public JsonNode getNamespaces() {
        log.info("获取所有命名空间");

        return webClient.get()
                .uri("/{apiVersion}/namespaces", k8sConfig.getApiVersion())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
