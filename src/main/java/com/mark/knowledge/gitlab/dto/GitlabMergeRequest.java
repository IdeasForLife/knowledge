package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * GitLab合并请求
 *
 * @author mark
 */
@Data
public class GitlabMergeRequest {

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

    @JsonProperty("merged_at")
    private String mergedAt;

    @JsonProperty("merged_by")
    private GitlabUser mergedBy;

    @JsonProperty("closed_at")
    private String closedAt;

    @JsonProperty("closed_by")
    private GitlabUser closedBy;

    @JsonProperty("target_branch")
    private String targetBranch;

    @JsonProperty("source_branch")
    private String sourceBranch;

    @JsonProperty("author")
    private GitlabUser author;

    @JsonProperty("assignees")
    private List<GitlabUser> assignees;

    @JsonProperty("reviewers")
    private List<GitlabUser> reviewers;

    @JsonProperty("source_project_id")
    private Long sourceProjectId;

    @JsonProperty("target_project_id")
    private Long targetProjectId;

    @JsonProperty("labels")
    private List<String> labels;

    @JsonProperty("work_in_progress")
    private Boolean workInProgress;

    @JsonProperty("milestone")
    private GitlabMilestone milestone;

    @JsonProperty("merge_when_pipeline_succeeds")
    private Boolean mergeWhenPipelineSucceeds;

    @JsonProperty("merge_status")
    private String mergeStatus;

    @JsonProperty("sha")
    private String sha;

    @JsonProperty("merge_commit_sha")
    private String mergeCommitSha;

    @JsonProperty("squash_commit_sha")
    private String squashCommitSha;

    @JsonProperty("user_notes_count")
    private Integer userNotesCount;

    @JsonProperty("discussion_locked")
    private Boolean discussionLocked;

    @JsonProperty("should_remove_source_branch")
    private Boolean shouldRemoveSourceBranch;

    @JsonProperty("force_remove_source_branch")
    private Boolean forceRemoveSourceBranch;

    @JsonProperty("references")
    private GitlabReferences references;

    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("has_conflicts")
    private Boolean hasConflicts;

    @JsonProperty("blocking_discussions_resolved")
    private Boolean blockingDiscussionsResolved;

    @JsonProperty("changes_count")
    private String changesCount;

    @JsonProperty("first_contribution")
    private Boolean firstContribution;

    @JsonProperty("draft")
    private Boolean draft;

    @JsonProperty("head_pipeline")
    private GitlabPipeline headPipeline;

    @JsonProperty("pipeline")
    private GitlabPipeline pipeline;
}
