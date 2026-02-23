package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes Service 对象
 *
 * @author mark
 */
@Data
public class K8sService {

    private String apiVersion;
    private String kind;
    private K8sObjectMetadata metadata;
    private K8sServiceSpec spec;
    private K8sServiceStatus status;

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
    public static class K8sServiceSpec {
        private List<K8sServicePort> ports;
        private K8sServiceSelector selector;
        private String type;
        private String clusterIP;
        private String externalIPs;
        private List<String> sessionAffinity;
    }

    @Data
    public static class K8sServicePort {
        private String name;
        private String protocol;
        private int port;
        private int targetPort;
        private int nodePort;
    }

    @Data
    public static class K8sServiceSelector {
        private Map<String, String> matchLabels;
    }

    @Data
    public static class K8sServiceStatus {
        private K8sServiceLoadBalancer loadBalancer;
    }

    @Data
    public static class K8sServiceLoadBalancer {
        private List<K8sIngress> ingress;
    }

    @Data
    public static class K8sIngress {
        private String ip;
        private String hostname;
    }
}
