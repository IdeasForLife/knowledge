package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes Deployment 对象
 *
 * @author mark
 */
@Data
public class K8sDeployment {

    private String apiVersion;
    private String kind;
    private K8sObjectMetadata metadata;
    private K8sDeploymentSpec spec;
    private K8sDeploymentStatus status;

    @Data
    public static class K8sObjectMetadata {
        private String name;
        private String namespace;
        private String uid;
        private String resourceVersion;
        private String creationTimestamp;
        private Map<String, String> labels;
        private Map<String, String> annotations;
    }

    @Data
    public static class K8sDeploymentSpec {
        private int replicas;
        private K8sDeploymentSelector selector;
        private K8sDeploymentTemplate template;
        private K8sDeploymentStrategy strategy;
    }

    @Data
    public static class K8sDeploymentSelector {
        private Map<String, String> matchLabels;
    }

    @Data
    public static class K8sDeploymentTemplate {
        private K8sPodTemplateMetadata metadata;
        private K8sPodTemplateSpec spec;
    }

    @Data
    public static class K8sPodTemplateMetadata {
        private Map<String, String> labels;
    }

    @Data
    public static class K8sPodTemplateSpec {
        private List<K8sPodContainer> containers;
    }

    @Data
    public static class K8sPodContainer {
        private String name;
        private String image;
        private List<K8sContainerPort> ports;
        private Map<String, String> resources;
    }

    @Data
    public static class K8sContainerPort {
        private String name;
        private int containerPort;
        private String protocol;
    }

    @Data
    public static class K8sDeploymentStrategy {
        private String type;
        private K8sRollingUpdateConfig rollingUpdate;
    }

    @Data
    public static class K8sRollingUpdateConfig {
        private int maxSurge;
        private int maxUnavailable;
    }

    @Data
    public static class K8sDeploymentStatus {
        private int observedGeneration;
        private int replicas;
        private int updatedReplicas;
        private int readyReplicas;
        private int availableReplicas;
        private int unavailableReplicas;
    }
}
