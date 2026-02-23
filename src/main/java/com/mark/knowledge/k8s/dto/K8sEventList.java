package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;

/**
 * Kubernetes 事件列表
 *
 * @author mark
 */
@Data
public class K8sEventList {
    private String apiVersion;
    private String kind;
    private K8sListMetadata metadata;
    private List<K8sEvent> items;
}
