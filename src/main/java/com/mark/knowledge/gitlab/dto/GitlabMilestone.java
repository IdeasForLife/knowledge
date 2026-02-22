package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab里程碑
 *
 * @author mark
 */
@Data
public class GitlabMilestone {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("iid")
    private Integer iid;

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private String state;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("due_date")
    private String dueDate;

    @JsonProperty("start_date")
    private String startDate;
}
