package com.mark.knowledge.k8s.dto;

import lombok.Data;

/**
 * Kubernetes 事件对象
 *
 * @author mark
 */
@Data
public class K8sEvent {

    private String apiVersion;
    private String kind;
    private K8sEventMetadata metadata;
    private String reason;
    private String message;
    private String type;
    private String firstTimestamp;
    private String lastTimestamp;
    private int count;
    private K8sObjectReference involvedObject;

    @Data
    public static class K8sEventMetadata {
        private String name;
        private String namespace;
        private String uid;
        private String resourceVersion;
        private String creationTimestamp;
    }

    @Data
    public static class K8sObjectReference {
        private String kind;
        private String namespace;
        private String name;
        private String uid;
        private String apiVersion;
        private String resourceVersion;
        private String fieldPath;
    }
}
