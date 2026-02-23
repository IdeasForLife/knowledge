package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes Pod 对象
 *
 * @author mark
 */
@Data
public class K8sPod {

    private String apiVersion;
    private String kind;
    private V1ObjectMeta metadata;
    private K8sPodSpec spec;
    private K8sPodStatus status;

    @Data
    public static class V1ObjectMeta {
        private String name;
        private String namespace;
        private String uid;
        private String resourceVersion;
        private String creationTimestamp;
        private Map<String, String> labels;
        private Map<String, String> annotations;
    }

    @Data
    public static class K8sPodSpec {
        private String nodeName;
        private List<K8sContainer> containers;
        private List<K8sContainer> initContainers;
        private String restartPolicy;
    }

    @Data
    public static class K8sContainer {
        private String name;
        private String image;
        private List<K8sContainerPort> ports;
        private List<K8sContainerEnvVar> env;
        private K8sResourceRequirements resources;
    }

    @Data
    public static class K8sContainerPort {
        private String name;
        private int containerPort;
        private String protocol;
    }

    @Data
    public static class K8sContainerEnvVar {
        private String name;
        private String value;
        private K8sEnvVarSource valueFrom;
    }

    @Data
    public static class K8sEnvVarSource {
        private K8sConfigMapKeySelector configMapKeyRef;
        private K8sSecretKeySelector secretKeyRef;
    }

    @Data
    public static class K8sConfigMapKeySelector {
        private String name;
        private String key;
    }

    @Data
    public static class K8sSecretKeySelector {
        private String name;
        private String key;
    }

    @Data
    public static class K8sResourceRequirements {
        private Map<String, String> limits;
        private Map<String, String> requests;
    }

    @Data
    public static class K8sPodStatus {
        private String phase;
        private String podIP;
        private String hostIP;
        private String startTime;
        private List<K8sContainerStatus> containerStatuses;
        private List<K8sContainerStatus> initContainerStatuses;
        private List<K8sPodCondition> conditions;
    }

    @Data
    public static class K8sContainerStatus {
        private String name;
        private K8sContainerState state;
        private K8sContainerState lastState;
        private boolean ready;
        private int restartCount;
        private String image;
        private String imageID;
    }

    @Data
    public static class K8sContainerState {
        private K8sContainerStateRunning running;
        private K8sContainerStateWaiting waiting;
        private K8sContainerStateTerminated terminated;
    }

    @Data
    public static class K8sContainerStateRunning {
        private String startedAt;
    }

    @Data
    public static class K8sContainerStateWaiting {
        private String reason;
        private String message;
    }

    @Data
    public static class K8sContainerStateTerminated {
        private int exitCode;
        private String reason;
        private String message;
        private String startedAt;
        private String finishedAt;
    }

    @Data
    public static class K8sPodCondition {
        private String type;
        private String status;
        private String lastTransitionTime;
        private String reason;
        private String message;
    }
}
