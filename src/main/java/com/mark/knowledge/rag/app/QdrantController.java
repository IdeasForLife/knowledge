package com.mark.knowledge.rag.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 集合管理控制器
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/qdrant")
public class QdrantController {

    private static final Logger log = LoggerFactory.getLogger(QdrantController.class);

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.http-port:6333}")
    private int qdrantHttpPort;

    /**
     * 获取所有集合列表
     *
     * @return 集合列表
     */
    @GetMapping("/collections")
    public ResponseEntity<?> getCollections() {
        try {
            log.info("获取Qdrant集合列表");

            String baseUrl = String.format("http://%s:%d", qdrantHost, qdrantHttpPort);
            WebClient webClient = WebClient.create(baseUrl);

            Map response = webClient
                    .get()
                    .uri("/collections")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections = (List<Map<String, Object>>) resultMap.get("collections");

            List<Map<String, Object>> collectionsInfo = new ArrayList<>();

            // 遍历每个集合，获取详细信息
            for (Map<String, Object> collection : collections) {
                String collectionName = (String) collection.get("name");

                try {
                    // 获取单个集合的详细信息
                    Map detailResponse = webClient
                            .get()
                            .uri("/collections/" + collectionName)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) detailResponse.get("result");

                    Map<String, Object> info = new HashMap<>();
                    info.put("name", collectionName);

                    // 从 result 中获取计数信息
                    info.put("pointsCount", result.get("points_count"));
                    info.put("segmentsCount", result.get("segments_count"));
                    info.put("indexedVectorsCount", result.get("indexed_vectors_count"));

                    // 从 config.params.vectors 获取向量配置
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> config = (Map<String, Object>) result.get("config");
                        if (config != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> params = (Map<String, Object>) config.get("params");
                            if (params != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");
                                if (vectors != null) {
                                    info.put("vectorSize", vectors.get("size"));
                                    info.put("distance", vectors.get("distance"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析集合 {} 向量配置失败: {}", collectionName, e.getMessage());
                        info.put("vectorSize", "N/A");
                        info.put("distance", "N/A");
                    }

                    collectionsInfo.add(info);

                } catch (Exception e) {
                    log.warn("获取集合 {} 详细信息失败: {}", collectionName, e.getMessage());
                    // 即使获取详细信息失败，也返回基本信息
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", collectionName);
                    info.put("vectorSize", "N/A");
                    info.put("distance", "N/A");
                    info.put("pointsCount", "N/A");
                    info.put("segmentsCount", "N/A");
                    collectionsInfo.add(info);
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "collections", collectionsInfo
            ));

        } catch (Exception e) {
            log.error("获取集合列表失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取集合列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除指定集合
     *
     * @param collectionName 集合名称
     * @return 删除结果
     */
    @DeleteMapping("/collections/{collectionName}")
    public ResponseEntity<?> deleteCollection(@PathVariable String collectionName) {
        try {
            log.info("删除Qdrant集合: {}", collectionName);

            String baseUrl = String.format("http://%s:%d", qdrantHost, qdrantHttpPort);
            WebClient webClient = WebClient.create(baseUrl);

            webClient
                    .delete()
                    .uri("/collections/" + collectionName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("集合 '{}' 删除成功", collectionName);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "集合 '" + collectionName + "' 删除成功"
            ));

        } catch (Exception e) {
            log.error("删除集合失败: {}", collectionName, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "删除集合失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取指定集合的详细信息
     *
     * @param collectionName 集合名称
     * @return 集合详细信息
     */
    @GetMapping("/collections/{collectionName}")
    public ResponseEntity<?> getCollectionInfo(@PathVariable String collectionName) {
        try {
            log.info("获取集合信息: {}", collectionName);

            String baseUrl = String.format("http://%s:%d", qdrantHost, qdrantHttpPort);
            WebClient webClient = WebClient.create(baseUrl);

            Map response = webClient
                    .get()
                    .uri("/collections/" + collectionName)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "collection", response.get("result")
            ));

        } catch (Exception e) {
            log.error("获取集合信息失败: {}", collectionName, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取集合信息失败: " + e.getMessage()
            ));
        }
    }
}
