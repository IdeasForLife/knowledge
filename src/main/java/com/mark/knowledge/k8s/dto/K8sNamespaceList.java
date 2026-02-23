package com.mark.knowledge.k8s.dto;

import lombok.Data;
import java.util.List;

/**
 * Kubernetes Namespace 列表
 *
 * @author mark
 */
@Data
public class K8sNamespaceList {
    private String apiVersion;
    private String kind;
    private K8sListMetadata metadata;
    private List<K8sNamespace> items;
}
