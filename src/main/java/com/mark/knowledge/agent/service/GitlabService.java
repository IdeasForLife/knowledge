package com.mark.knowledge.agent.service;

import com.mark.knowledge.gitlab.GitlabUtil;
import com.mark.knowledge.gitlab.dto.*;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GitLab Agent服务 - 为LLM提供GitLab工具调用能力
 *
 * 功能：
 * - 查询项目分支
 * - 查看提交历史
 * - 查看合并请求
 * - 分析代码变更
 * - 获取文件差异
 *
 * @author mark
 */
@Component
public class GitlabService {

    private static final Logger log = LoggerFactory.getLogger(GitlabService.class);

    private final GitlabUtil gitlabUtil;

    public GitlabService(GitlabUtil gitlabUtil) {
        this.gitlabUtil = gitlabUtil;
    }

    // ==================== 连接和项目信息 ====================

    /**
     * 测试GitLab连接
     */
    @Tool("测试GitLab服务器连接是否正常")
    public String testGitlabConnection() {
        log.info("LLM调用GitLab连接测试");

        boolean success = gitlabUtil.testConnection();

        if (success) {
            return "✓ GitLab连接成功\n服务器通信正常，可以进行后续操作。";
        } else {
            return "✗ GitLab连接失败\n请检查服务器地址和访问令牌配置。";
        }
    }

    /**
     * 获取项目ID
     */
    @Tool("获取GitLab项目的ID。需要提供项目路径，如 'group/project' 或 'username/repository'")
    public String getProjectId(String projectPath) {
        log.info("LLM调用获取项目ID: {}", projectPath);

        if (projectPath == null || projectPath.trim().isEmpty()) {
            return "错误：请提供项目路径，例如 'group/project' 或 'username/repository'";
        }

        Long id = gitlabUtil.getProjectId(projectPath);

        if (id != null) {
            return String.format("✓ 项目 `%s` 的ID是: %d", projectPath, id);
        } else {
            return String.format("✗ 未找到项目: %s\n请检查项目路径是否正确。", projectPath);
        }
    }

    // ==================== 分支相关 ====================

    /**
     * 获取项目分支列表
     */
    @Tool("获取GitLab项目的所有分支列表。需要提供项目路径或ID")
    public String getProjectBranches(String projectIdOrPath) {
        log.info("LLM调用获取分支列表: {}", projectIdOrPath);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }

        List<GitlabBranch> branches = gitlabUtil.getBranches(projectIdOrPath);

        if (branches.isEmpty()) {
            return String.format("项目 `%s` 没有找到任何分支", projectIdOrPath);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 项目 `%s` 的分支列表\n\n", projectIdOrPath));
        result.append(String.format("共有 %d 个分支\n\n", branches.size()));

        for (GitlabBranch branch : branches) {
            String marker = branch.getDefaultBranch() ? " (默认)" : "";
            String protectedMarker = branch.getProtectedBranch() != null && branch.getProtectedBranch() ? " [受保护]" : "";
            result.append(String.format("- **%s**%s%s\n", branch.getName(), marker, protectedMarker));
        }

        return result.toString();
    }

    /**
     * 获取默认分支
     */
    @Tool("获取GitLab项目的默认分支名称。需要提供项目路径或ID")
    public String getDefaultBranch(String projectIdOrPath) {
        log.info("LLM调用获取默认分支: {}", projectIdOrPath);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }

        String defaultBranch = gitlabUtil.getDefaultBranch(projectIdOrPath);

        if (defaultBranch != null) {
            return String.format("✓ 项目 `%s` 的默认分支是: **%s**", projectIdOrPath, defaultBranch);
        } else {
            return String.format("✗ 无法获取项目 `%s` 的默认分支", projectIdOrPath);
        }
    }

    // ==================== 提交相关 ====================

    /**
     * 获取提交历史
     */
    @Tool("获取GitLab项目的提交历史。参数：项目路径/ID，分支名称（可选，默认默认分支），返回数量（可选，默认20条）")
    public String getCommitHistory(String projectIdOrPath, String branch, Integer limit) {
        log.info("LLM调用获取提交历史: {}, branch: {}, limit: {}", projectIdOrPath, branch, limit);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }

        int count = limit != null && limit > 0 ? Math.min(limit, 100) : 20;

        List<GitlabCommit> commits = gitlabUtil.getCommits(projectIdOrPath, branch, count, 1);

        if (commits.isEmpty()) {
            return String.format("项目 `%s` 没有找到提交记录", projectIdOrPath);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 项目 `%s` 的提交历史\n\n", projectIdOrPath));
        result.append(String.format("找到 %d 条提交记录（显示最近 %d 条）\n\n", commits.size(), count));

        for (GitlabCommit commit : commits) {
            result.append(String.format("## %s\n", commit.getShortId()));
            result.append(String.format("**作者:** %s <%s>\n", commit.getAuthorName(), commit.getAuthorEmail()));
            result.append(String.format("**时间:** %s\n", commit.getCreatedAt()));
            result.append(String.format("**标题:** %s\n", commit.getTitle()));

            if (commit.getMessage() != null && !commit.getMessage().equals(commit.getTitle())) {
                String msg = commit.getMessage().split("\n")[0];
                if (!msg.isEmpty()) {
                    result.append(String.format("**描述:** %s\n", msg));
                }
            }

            if (commit.getStats() != null) {
                result.append(String.format("**变更:** +%d 行添加, -%d 行删除\n",
                        commit.getStats().getAdditions(),
                        commit.getStats().getDeletions()));
            }

            result.append(String.format("**链接:** %s\n\n", commit.getWebUrl()));
        }

        return result.toString();
    }

    /**
     * 获取提交详情
     */
    @Tool("获取单个GitLab提交的详细信息。参数：项目路径/ID，提交SHA或短SHA")
    public String getCommitDetails(String projectIdOrPath, String sha) {
        log.info("LLM调用获取提交详情: {}, sha: {}", projectIdOrPath, sha);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }
        if (sha == null || sha.trim().isEmpty()) {
            return "错误：请提供提交SHA或短SHA";
        }

        GitlabCommit commit = gitlabUtil.getCommit(projectIdOrPath, sha);

        if (commit == null) {
            return String.format("✗ 未找到提交: %s", sha);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 提交详情: %s\n\n", commit.getShortId()));
        result.append(String.format("**完整SHA:** %s\n", commit.getId()));
        result.append(String.format("**作者:** %s <%s>\n", commit.getAuthorName(), commit.getAuthorEmail()));
        result.append(String.format("**提交者:** %s <%s>\n", commit.getCommitterName(), commit.getCommitterEmail()));
        result.append(String.format("**作者日期:** %s\n", commit.getAuthoredDate()));
        result.append(String.format("**提交日期:** %s\n", commit.getCreatedAt()));
        result.append(String.format("**标题:** %s\n", commit.getTitle()));
        result.append(String.format("**消息:**\n```\n%s\n```\n", commit.getMessage()));

        if (commit.getStats() != null) {
            result.append(String.format("**统计:** +%d / -%d / 总计 %d\n",
                    commit.getStats().getAdditions(),
                    commit.getStats().getDeletions(),
                    commit.getStats().getTotal()));
        }

        result.append(String.format("**链接:** %s\n", commit.getWebUrl()));

        if (commit.getLastPipeline() != null) {
            result.append(String.format("**流水线:** [ #%d ](%s) - %s\n",
                    commit.getLastPipeline().getId(),
                    commit.getLastPipeline().getWebUrl(),
                    commit.getLastPipeline().getStatus()));
        }

        return result.toString();
    }

    /**
     * 获取提交的文件差异
     */
    @Tool("获取GitLab提交的文件变更差异。参数：项目路径/ID，提交SHA或短SHA")
    public String getCommitDiff(String projectIdOrPath, String sha) {
        log.info("LLM调用获取提交差异: {}, sha: {}", projectIdOrPath, sha);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }
        if (sha == null || sha.trim().isEmpty()) {
            return "错误：请提供提交SHA";
        }

        List<GitlabDiff> diffs = gitlabUtil.getCommitDiff(projectIdOrPath, sha);

        if (diffs.isEmpty()) {
            return String.format("提交 %s 没有文件变更", sha);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 提交 %s 的文件变更\n\n", sha));
        result.append(String.format("共 %d 个文件发生变更\n\n", diffs.size()));

        for (GitlabDiff diff : diffs) {
            result.append(String.format("## %s\n", diff.getNewPath()));

            if (diff.getNewFile() != null && diff.getNewFile()) {
                result.append("**状态:** 新增文件\n");
            } else if (diff.getDeletedFile() != null && diff.getDeletedFile()) {
                result.append("**状态:** 删除文件\n");
            } else if (diff.getRenamedFile() != null && diff.getRenamedFile()) {
                result.append(String.format("**状态:** 重命名 (原路径: %s)\n", diff.getOldPath()));
            } else {
                result.append("**状态:** 修改\n");
            }

            // 截取diff内容，避免过长
            String diffContent = diff.getDiff();
            if (diffContent != null && diffContent.length() > 2000) {
                diffContent = diffContent.substring(0, 2000) + "\n... (内容过长，已截断)";
            }

            result.append(String.format("**差异:**\n```\n%s\n```\n\n", diffContent));
        }

        return result.toString();
    }

    // ==================== 合并请求相关 ====================

    /**
     * 获取合并请求列表
     */
    @Tool("获取GitLab项目的合并请求列表。参数：项目路径/ID，状态（可选：opened/closed/merged/all，默认opened），数量（可选，默认20）")
    public String getMergeRequests(String projectIdOrPath, String state, Integer limit) {
        log.info("LLM调用获取合并请求: {}, state: {}, limit: {}", projectIdOrPath, state, limit);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }

        String mrState = (state != null && !state.isEmpty()) ? state : "opened";
        int count = limit != null && limit > 0 ? Math.min(limit, 100) : 20;

        List<GitlabMergeRequest> mrs = gitlabUtil.getMergeRequests(projectIdOrPath, mrState, count, 1);

        if (mrs.isEmpty()) {
            return String.format("项目 `%s` 没有找到状态为 '%s' 的合并请求", projectIdOrPath, mrState);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 项目 `%s` 的合并请求列表 (状态: %s)\n\n", projectIdOrPath, mrState));
        result.append(String.format("找到 %d 个合并请求\n\n", mrs.size()));

        for (GitlabMergeRequest mr : mrs) {
            result.append(String.format("## !%d %s\n", mr.getIid(), mr.getTitle()));

            if (mr.getAuthor() != null) {
                result.append(String.format("**作者:** %s\n", mr.getAuthor().getName()));
            }

            result.append(String.format("**状态:** %s\n", mr.getState()));
            result.append(String.format("**分支:** %s → %s\n", mr.getSourceBranch(), mr.getTargetBranch()));
            result.append(String.format("**创建时间:** %s\n", mr.getCreatedAt()));

            if (mr.getMergedAt() != null) {
                result.append(String.format("**合并时间:** %s\n", mr.getMergedAt()));
            }

            if (!mr.getLabels().isEmpty()) {
                result.append(String.format("**标签:** %s\n", String.join(", ", mr.getLabels())));
            }

            if (mr.getMilestone() != null) {
                result.append(String.format("**里程碑:** %s\n", mr.getMilestone().getTitle()));
            }

            if (mr.getAssignees() != null && !mr.getAssignees().isEmpty()) {
                String assignees = mr.getAssignees().stream()
                        .map(GitlabUser::getName)
                        .collect(Collectors.joining(", "));
                result.append(String.format("**指派给:** %s\n", assignees));
            }

            result.append(String.format("**链接:** %s\n\n", mr.getWebUrl()));
        }

        return result.toString();
    }

    /**
     * 获取合并请求详情
     */
    @Tool("获取单个GitLab合并请求的详细信息。参数：项目路径/ID，合并请求IID（如：42）")
    public String getMergeRequestDetails(String projectIdOrPath, Integer mergeRequestIid) {
        log.info("LLM调用获取MR详情: {}, iid: {}", projectIdOrPath, mergeRequestIid);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }
        if (mergeRequestIid == null) {
            return "错误：请提供合并请求的IID";
        }

        GitlabMergeRequest mr = gitlabUtil.getMergeRequest(projectIdOrPath, mergeRequestIid);

        if (mr == null) {
            return String.format("✗ 未找到合并请求: !%d", mergeRequestIid);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 合并请求详情: !%d\n\n", mr.getIid()));
        result.append(String.format("**标题:** %s\n", mr.getTitle()));

        if (mr.getDescription() != null && !mr.getDescription().isEmpty()) {
            String desc = mr.getDescription().length() > 1000
                    ? mr.getDescription().substring(0, 1000) + "..."
                    : mr.getDescription();
            result.append(String.format("**描述:**\n%s\n", desc));
        }

        result.append(String.format("**状态:** %s\n", mr.getState()));
        result.append(String.format("**源分支:** %s\n", mr.getSourceBranch()));
        result.append(String.format("**目标分支:** %s\n", mr.getTargetBranch()));

        if (mr.getAuthor() != null) {
            result.append(String.format("**作者:** %s\n", mr.getAuthor().getName()));
        }

        result.append(String.format("**创建时间:** %s\n", mr.getCreatedAt()));
        result.append(String.format("**更新时间:** %s\n", mr.getUpdatedAt()));

        if (mr.getMergedAt() != null && mr.getMergedBy() != null) {
            result.append(String.format("**合并者:** %s\n", mr.getMergedBy().getName()));
            result.append(String.format("**合并时间:** %s\n", mr.getMergedAt()));
        }

        if (!mr.getLabels().isEmpty()) {
            result.append(String.format("**标签:** %s\n", String.join(", ", mr.getLabels())));
        }

        if (mr.getMilestone() != null) {
            result.append(String.format("**里程碑:** %s\n", mr.getMilestone().getTitle()));
        }

        result.append(String.format("**变更数:** %s\n", mr.getChangesCount()));
        result.append(String.format("**讨论数:** %d\n", mr.getUserNotesCount()));

        if (mr.getDraft() != null && mr.getDraft()) {
            result.append("**草稿:** 是\n");
        }

        if (mr.getHasConflicts() != null && mr.getHasConflicts()) {
            result.append("**冲突:** 有冲突 ⚠️\n");
        }

        result.append(String.format("**链接:** %s\n", mr.getWebUrl()));

        if (mr.getHeadPipeline() != null) {
            result.append(String.format("**流水线:** [ #%d ](%s) - %s\n",
                    mr.getHeadPipeline().getId(),
                    mr.getHeadPipeline().getWebUrl(),
                    mr.getHeadPipeline().getStatus()));
        }

        return result.toString();
    }

    /**
     * 获取合并请求的文件变更
     */
    @Tool("获取GitLab合并请求的文件变更。参数：项目路径/ID，合并请求IID")
    public String getMergeRequestChanges(String projectIdOrPath, Integer mergeRequestIid) {
        log.info("LLM调用获取MR变更: {}, iid: {}", projectIdOrPath, mergeRequestIid);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }
        if (mergeRequestIid == null) {
            return "错误：请提供合并请求IID";
        }

        List<GitlabFileChange> changes = gitlabUtil.getMergeRequestChanges(projectIdOrPath, mergeRequestIid);

        if (changes.isEmpty()) {
            return String.format("合并请求 !%d 没有文件变更", mergeRequestIid);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 合并请求 !%d 的文件变更\n\n", mergeRequestIid));
        result.append(String.format("共 %d 个文件发生变更\n\n", changes.size()));

        int addedFiles = 0, deletedFiles = 0, modifiedFiles = 0;
        int totalAdditions = 0, totalDeletions = 0;

        for (GitlabFileChange change : changes) {
            if (change.getNewFile() != null && change.getNewFile()) {
                addedFiles++;
            } else if (change.getDeletedFile() != null && change.getDeletedFile()) {
                deletedFiles++;
            } else {
                modifiedFiles++;
            }

            result.append(String.format("## %s\n", change.getNewPath()));

            if (change.getNewFile() != null && change.getNewFile()) {
                result.append("**状态:** 新增\n");
            } else if (change.getDeletedFile() != null && change.getDeletedFile()) {
                result.append("**状态:** 删除\n");
            } else if (change.getRenamedFile() != null && change.getRenamedFile()) {
                result.append(String.format("**状态:** 重命名 (原: %s)\n", change.getOldPath()));
            } else {
                result.append("**状态:** 修改\n");
            }

            // 截取diff内容
            String diffContent = change.getDiff();
            if (diffContent != null) {
                // 统计行数
                String[] lines = diffContent.split("\n");
                for (String line : lines) {
                    if (line.startsWith("+") && !line.startsWith("+++")) totalAdditions++;
                    if (line.startsWith("-") && !line.startsWith("---")) totalDeletions++;
                }

                if (diffContent.length() > 2000) {
                    diffContent = diffContent.substring(0, 2000) + "\n... (内容过长，已截断)";
                }
                result.append(String.format("**差异:**\n```\n%s\n```\n\n", diffContent));
            } else {
                result.append("\n");
            }
        }

        // 添加统计摘要
        result.append("## 变更统计\n\n");
        result.append(String.format("- 新增文件: %d\n", addedFiles));
        result.append(String.format("- 删除文件: %d\n", deletedFiles));
        result.append(String.format("- 修改文件: %d\n", modifiedFiles));
        result.append(String.format("- 新增行数: +%d\n", totalAdditions));
        result.append(String.format("- 删除行数: -%d\n", totalDeletions));

        return result.toString();
    }

    /**
     * 获取合并请求的提交历史
     */
    @Tool("获取GitLab合并请求的提交历史。参数：项目路径/ID，合并请求IID")
    public String getMergeRequestCommits(String projectIdOrPath, Integer mergeRequestIid) {
        log.info("LLM调用获取MR提交: {}, iid: {}", projectIdOrPath, mergeRequestIid);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }
        if (mergeRequestIid == null) {
            return "错误：请提供合并请求IID";
        }

        List<GitlabCommit> commits = gitlabUtil.getMergeRequestCommits(projectIdOrPath, mergeRequestIid);

        if (commits.isEmpty()) {
            return String.format("合并请求 !%d 没有提交记录", mergeRequestIid);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 合并请求 !%d 的提交历史\n\n", mergeRequestIid));
        result.append(String.format("共 %d 条提交\n\n", commits.size()));

        for (GitlabCommit commit : commits) {
            result.append(String.format("## %s\n", commit.getShortId()));
            result.append(String.format("**作者:** %s\n", commit.getAuthorName()));
            result.append(String.format("**时间:** %s\n", commit.getCreatedAt()));
            result.append(String.format("**标题:** %s\n", commit.getTitle()));

            if (commit.getStats() != null) {
                result.append(String.format("**变更:** +%d -%d\n",
                        commit.getStats().getAdditions(),
                        commit.getStats().getDeletions()));
            }

            result.append(String.format("**链接:** %s\n\n", commit.getWebUrl()));
        }

        return result.toString();
    }

    // ==================== 代码分析相关 ====================

    /**
     * 获取最近的代码变更摘要（用于Agent代码分析）
     */
    @Tool("获取GitLab项目最近的代码变更摘要。参数：项目路径/ID，分支名（可选），天数（可选，默认7天）。适用于代码审查和变更分析")
    public String getRecentCodeChanges(String projectIdOrPath, String branch, Integer days) {
        log.info("LLM调用获取最近代码变更: {}, branch: {}, days: {}", projectIdOrPath, branch, days);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }

        return gitlabUtil.getRecentChangesSummary(projectIdOrPath, branch, days);
    }

    /**
     * 分析合并请求的代码变更
     */
    @Tool("分析GitLab合并请求的代码变更。参数：项目路径/ID，合并请求IID。返回详细的文件变更和代码差异，用于代码审查")
    public String analyzeMergeRequest(String projectIdOrPath, Integer mergeRequestIid) {
        log.info("LLM调用分析MR: {}, iid: {}", projectIdOrPath, mergeRequestIid);

        if (projectIdOrPath == null || projectIdOrPath.trim().isEmpty()) {
            return "错误：请提供项目路径或ID";
        }
        if (mergeRequestIid == null) {
            return "错误：请提供合并请求IID";
        }

        // 获取MR详情
        GitlabMergeRequest mr = gitlabUtil.getMergeRequest(projectIdOrPath, mergeRequestIid);
        if (mr == null) {
            return String.format("✗ 未找到合并请求: !%d", mergeRequestIid);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("# 合并请求代码分析: !%d\n\n", mr.getIid()));
        result.append(String.format("**标题:** %s\n", mr.getTitle()));
        result.append(String.format("**作者:** %s\n", mr.getAuthor() != null ? mr.getAuthor().getName() : "未知"));
        result.append(String.format("**分支:** %s → %s\n\n", mr.getSourceBranch(), mr.getTargetBranch()));

        // 获取变更
        List<GitlabFileChange> changes = gitlabUtil.getMergeRequestChanges(projectIdOrPath, mergeRequestIid);

        if (changes.isEmpty()) {
            result.append("此合并请求没有文件变更\n");
            return result.toString();
        }

        result.append(String.format("## 变更概览\n\n共 %d 个文件发生变更\n\n", changes.size()));

        // 文件列表
        result.append("### 变更文件列表\n\n");
        for (GitlabFileChange change : changes) {
            String status;
            if (change.getNewFile() != null && change.getNewFile()) {
                status = "[新增]";
            } else if (change.getDeletedFile() != null && change.getDeletedFile()) {
                status = "[删除]";
            } else if (change.getRenamedFile() != null && change.getRenamedFile()) {
                status = "[重命名]";
            } else {
                status = "[修改]";
            }
            result.append(String.format("- %s `%s`\n", status, change.getNewPath()));
        }

        result.append("\n## 详细变更\n\n");

        // 详细差异
        for (GitlabFileChange change : changes) {
            result.append(String.format("### %s\n\n", change.getNewPath()));

            String diffContent = change.getDiff();
            if (diffContent != null) {
                if (diffContent.length() > 3000) {
                    diffContent = diffContent.substring(0, 3000) + "\n\n... (内容过长，已截断，请查看GitLab获取完整差异)";
                }
                result.append(String.format("```diff\n%s\n```\n\n", diffContent));
            }
        }

        return result.toString();
    }
}
