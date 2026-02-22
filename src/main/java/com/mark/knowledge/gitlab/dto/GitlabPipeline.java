package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab流水线
 *
 * @author mark
 */
@Data
public class GitlabPipeline {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("sha")
    private String sha;

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("status")
    private String status;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("duration")
    private Integer duration;

    @JsonProperty("coverage")
    private String coverage;
}
