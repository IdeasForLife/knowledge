package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GitLab提交信息
 *
 * @author mark
 */
@Data
public class GitlabCommit {

    @JsonProperty("id")
    private String id;

    @JsonProperty("short_id")
    private String shortId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("message")
    private String message;

    @JsonProperty("author_name")
    private String authorName;

    @JsonProperty("author_email")
    private String authorEmail;

    @JsonProperty("authored_date")
    private String authoredDate;

    @JsonProperty("committer_name")
    private String committerName;

    @JsonProperty("committer_email")
    private String committerEmail;

    @JsonProperty("committed_date")
    private String committedDate;

    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("stats")
    private GitlabCommitStats stats;

    @JsonProperty("status")
    private String status;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("last_pipeline")
    private GitlabPipeline lastPipeline;
}
