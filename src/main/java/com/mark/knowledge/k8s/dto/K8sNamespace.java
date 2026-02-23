package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes Namespace 对象
 *
 * @author mark
 */
@Data
public class K8sNamespace {

    private String apiVersion;
    private String kind;
    private K8sNamespaceMetadata metadata;
    private K8sNamespaceSpec spec;
    private K8sNamespaceStatus status;

    @Data
    public static class K8sNamespaceMetadata {
        private String name;
        private String uid;
        private String resourceVersion;
        private String creationTimestamp;
        private Map<String, String> labels;
        private Map<String, String> annotations;
    }

    @Data
    public static class K8sNamespaceSpec {
        private List<String> finalizers;
    }

    @Data
    public static class K8sNamespaceStatus {
        private String phase;
    }
}
