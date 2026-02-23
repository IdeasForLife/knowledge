package com.mark.knowledge.k8s.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Kubernetes 列表元数据
 *
 * @author mark
 */
@Data
public class K8sListMetadata {
    private String resourceVersion;

    @JsonProperty("continue")
    private String continueToken;

    private String remainingItemCount;
}
