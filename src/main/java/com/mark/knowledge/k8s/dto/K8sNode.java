package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes 节点对象
 *
 * @author mark
 */
@Data
public class K8sNode {

    private String apiVersion;
    private String kind;
    private K8sObjectMetadata metadata;
    private K8sNodeSpec spec;
    private K8sNodeStatus status;

    @Data
    public static class K8sObjectMetadata {
        private String name;
        private String uid;
        private String resourceVersion;
        private String creationTimestamp;
        private Map<String, String> labels;
        private Map<String, String> annotations;
    }

    @Data
    public static class K8sNodeSpec {
        private String podCIDR;
        private String providerID;
        private List<String> taints;
    }

    @Data
    public static class K8sNodeStatus {
        private K8sNodeSystemInfo nodeInfo;
        private List<K8sNodeCondition> conditions;
        private List<K8sNodeAddress> addresses;
        private K8sNodeDaemonEndpoints daemonEndpoints;
        private K8sNodeAllocatedResources capacity;
        private K8sNodeAllocatedResources allocatable;
    }

    @Data
    public static class K8sNodeSystemInfo {
        private String machineID;
        private String systemUUID;
        private String bootID;
        private String kernelVersion;
        private String osImage;
        private String containerRuntimeVersion;
        private String kubeletVersion;
        private String kubeProxyVersion;
        private String operatingSystem;
        private String architecture;
    }

    @Data
    public static class K8sNodeCondition {
        private String type;
        private String status;
        private String lastHeartbeatTime;
        private String lastTransitionTime;
        private String reason;
        private String message;
    }

    @Data
    public static class K8sNodeAddress {
        private String type;
        private String address;
    }

    @Data
    public static class K8sNodeDaemonEndpoints {
        private K8sNodeDaemonEndpoint kubeletEndpoint;
    }

    @Data
    public static class K8sNodeDaemonEndpoint {
        private String Port;
    }

    @Data
    public static class K8sNodeAllocatedResources {
        private Map<String, String> cpu;
        private Map<String, String> memory;
        private Map<String, String> pods;
        private Map<String, String> ephemeralStorage;
    }
}
