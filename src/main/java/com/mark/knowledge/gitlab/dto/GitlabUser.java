package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab用户信息
 *
 * @author mark
 */
@Data
public class GitlabUser {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("name")
    private String name;

    @JsonProperty("state")
    private String state;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonProperty("web_url")
    private String webUrl;
}
