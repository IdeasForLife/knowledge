package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab分支信息
 *
 * @author mark
 */
@Data
public class GitlabBranch {

    @JsonProperty("name")
    private String name;

    @JsonProperty("merged")
    private Boolean merged;

    @JsonProperty("protected")
    private Boolean protectedBranch;

    @JsonProperty("default")
    private Boolean defaultBranch;

    @JsonProperty("developers_can_push")
    private Boolean developersCanPush;

    @JsonProperty("developers_can_merge")
    private Boolean developersCanMerge;

    @JsonProperty("can_push")
    private Boolean canPush;

    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("commit")
    private GitlabCommit commit;
}
