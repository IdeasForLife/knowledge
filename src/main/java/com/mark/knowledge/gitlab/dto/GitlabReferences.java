package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab引用信息
 *
 * @author mark
 */
@Data
public class GitlabReferences {

    @JsonProperty("short")
    private String shortRef;

    @JsonProperty("relative")
    private String relative;

    @JsonProperty("full")
    private String full;
}
