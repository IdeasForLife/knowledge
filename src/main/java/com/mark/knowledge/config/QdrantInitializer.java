package com.mark.knowledge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Qdrant集合初始化器
 *
 * 应用启动时自动检查并创建Qdrant集合
 * - 检查集合是否存在
 * - 如果不存在则创建新集合
 * - 如果维度不匹配则删除并重新创建
 * - 使用REST API与Qdrant通信
 */
@Component
public class QdrantInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QdrantInitializer.class);

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.http-port:6333}")
    private int qdrantHttpPort;

    @Value("${qdrant.collection-name:knowledge-base}")
    private String collectionName;

    @Value("${qdrant.vector-size:1024}")
    private int vectorSize;

    @Value("${qdrant.create-collection-if-not-exists:true}")
    private boolean createCollectionIfNeeded;

    /**
     * 应用启动后执行
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!createCollectionIfNeeded) {
            log.info("跳过Qdrant集合自动创建（已通过配置禁用）");
            return;
        }

        log.info("==========================================");
        log.info("Qdrant集合初始化");
        log.info("==========================================");

        String baseUrl = String.format("http://%s:%d", qdrantHost, qdrantHttpPort);
        WebClient webClient = WebClient.create(baseUrl);

        try {
            // 步骤1：检查集合是否存在以及维度是否匹配
            log.info("检查集合: {}", collectionName);

            CollectionInfo collectionInfo = getCollectionInfo(webClient);

            if (collectionInfo == null) {
                // 集合不存在，创建新集合
                log.info("集合 '{}' 不存在", collectionName);
                log.info("创建集合: {}", collectionName);
                createCollection(webClient);
                log.info("集合 '{}' 创建成功", collectionName);
            } else if (collectionInfo.vectorSize != vectorSize) {
                // 集合存在但维度不匹配，删除并重建
                log.warn("集合 '{}' 已存在，但维度不匹配", collectionName);
                log.warn("当前维度: {}", collectionInfo.vectorSize);
                log.warn("期望维度: {}", vectorSize);
                log.info("删除旧集合...");
                deleteCollection(webClient);
                log.info("旧集合已删除");
                log.info("创建新集合: {}", collectionName);
                createCollection(webClient);
                log.info("集合 '{}' 重建成功", collectionName);
            } else {
                // 集合存在且维度匹配
                log.info("集合 '{}' 已存在且维度匹配", collectionName);
                log.info("向量维度: {}", vectorSize);
            }

        } catch (Exception e) {
            log.warn("集合初始化失败: {}", e.getMessage());
            log.warn("应用将继续启动。请确保集合已存在且配置正确。");
        }

        log.info("==========================================");
    }

    /**
     * 获取集合信息
     *
     * @param webClient WebClient实例
     * @return 集合信息，如果不存在则返回null
     */
    private CollectionInfo getCollectionInfo(WebClient webClient) {
        try {
            Mono<Map> response = webClient
                    .get()
                    .uri("/collections/" + collectionName)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map>() {});

            Map result = response.block();

            if (result != null && result.containsKey("result")) {
                Map<String, Object> resultMap = (Map<String, Object>) result.get("result");
                Map<String, Object> params = (Map<String, Object>) resultMap.get("params");

                // 检查 params 是否为 null
                if (params == null) {
                    log.warn("⚠️  集合 '{}' 的 params 为 null，尝试获取配置", collectionName);
                    // 尝试从 config 中获取
                    Map<String, Object> config = (Map<String, Object>) resultMap.get("config");
                    if (config != null && config.containsKey("params")) {
                        params = (Map<String, Object>) config.get("params");
                    }
                }

                if (params != null && params.containsKey("vectors")) {
                    Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");
                    if (vectors != null && vectors.containsKey("size")) {
                        Number size = (Number) vectors.get("size");
                        return new CollectionInfo(size.intValue());
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("集合检查异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 创建Qdrant集合
     *
     * @param webClient WebClient实例
     */
    private void createCollection(WebClient webClient) {
        try {
            // 先检查集合是否已存在
            CollectionInfo existing = getCollectionInfo(webClient);
            if (existing != null) {
                log.info("✓ 集合 '{}' 已存在，跳过创建", collectionName);
                return;
            }

            // 构建创建集合的请求体
            String requestBody = String.format("""
                {
                    "vectors": {
                        "size": %d,
                        "distance": "Cosine"
                    }
                }
                """, vectorSize);

            String response = webClient
                    .put()
                    .uri("/collections/" + collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("创建响应: {}", response);

        } catch (Exception e) {
            // 处理 409 Conflict（集合已存在）的情况
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                log.warn("⚠️  集合 '{}' 已存在（409 Conflict），跳过创建", collectionName);
                return;
            }
            log.error("❌ 创建集合失败: {}", e.getMessage());
            throw new RuntimeException("创建Qdrant集合失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除Qdrant集合
     *
     * @param webClient WebClient实例
     */
    private void deleteCollection(WebClient webClient) {
        try {
            webClient
                    .delete()
                    .uri("/collections/" + collectionName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("删除集合失败: {}", e.getMessage());
            throw new RuntimeException("删除Qdrant集合失败: " + e.getMessage(), e);
        }
    }

    /**
     * 集合信息
     */
    private static class CollectionInfo {
        int vectorSize;

        CollectionInfo(int vectorSize) {
            this.vectorSize = vectorSize;
        }
    }
}
