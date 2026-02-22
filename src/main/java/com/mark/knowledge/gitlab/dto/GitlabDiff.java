package com.mark.knowledge.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab代码差异
 *
 * @author mark
 */
@Data
public class GitlabDiff {

    @JsonProperty("old_path")
    private String oldPath;

    @JsonProperty("new_path")
    private String newPath;

    @JsonProperty("diff")
    private String diff;

    @JsonProperty("new_file")
    private Boolean newFile;

    @JsonProperty("renamed_file")
    private Boolean renamedFile;

    @JsonProperty("deleted_file")
    private Boolean deletedFile;
}
