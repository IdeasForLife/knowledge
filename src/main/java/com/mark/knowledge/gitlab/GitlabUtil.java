package com.mark.knowledge.gitlab;

import com.mark.knowledge.gitlab.config.GitlabConfig;
import com.mark.knowledge.gitlab.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GitLab工具类 - 提供与GitLab API交互的基础方法
 *
 * GitLab API参考：
 * GET /projects/:id/merge_requests
 * GET /projects/:id/merge_requests?state=opened
 * GET /projects/:id/merge_requests?state=all
 * GET /projects/:id/merge_requests?iids[]=42&iids[]=43
 * GET /projects/:id/repository/commits
 * GET /projects/:id/repository/branches
 *
 * @author mark
 */
@Component
public class GitlabUtil {

    private static final Logger log = LoggerFactory.getLogger(GitlabUtil.class);

    private final GitlabConfig gitlabConfig;
    private final WebClient webClient;

    public GitlabUtil(GitlabConfig gitlabConfig) {
        this.gitlabConfig = gitlabConfig;
        this.webClient = WebClient.builder()
                .baseUrl(gitlabConfig.getUrl() + gitlabConfig.getApiVersion())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gitlabConfig.getToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ==================== 项目相关 ====================

    /**
     * 根据项目路径获取项目ID
     *
     * @param projectPath 项目路径，如 "group/project"
     * @return 项目ID
     */
    public Long getProjectId(String projectPath) {
        try {
            log.debug("获取项目ID: {}", projectPath);

            Map<String, Object> project = webClient.get()
                    .uri("/projects/{path}", projectPath)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (project != null) {
                Long id = ((Number) project.get("id")).longValue();
                log.debug("项目 {} 的ID: {}", projectPath, id);
                return id;
            }
            return null;
        } catch (Exception e) {
            log.error("获取项目ID失败: {}", projectPath, e);
            return null;
        }
    }

    // ==================== 分支相关 ====================

    /**
     * 获取项目所有分支
     *
     * @param projectIdOrPath 项目ID或路径
     * @return 分支列表
     */
    public List<GitlabBranch> getBranches(String projectIdOrPath) {
        try {
            log.debug("获取项目分支: {}", projectIdOrPath);

            List<GitlabBranch> branches = webClient.get()
                    .uri("/projects/{projectIdOrPath}/repository/branches", projectIdOrPath)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GitlabBranch>>() {})
                    .block();

            log.debug("项目 {} 共有 {} 个分支", projectIdOrPath, branches != null ? branches.size() : 0);
            return branches;
        } catch (Exception e) {
            log.error("获取分支列表失败: {}", projectIdOrPath, e);
            return List.of();
        }
    }

    /**
     * 获取默认分支
     *
     * @param projectIdOrPath 项目ID或路径
     * @return 默认分支名称
     */
    public String getDefaultBranch(String projectIdOrPath) {
        try {
            log.debug("获取默认分支: {}", projectIdOrPath);

            Map<String, Object> project = webClient.get()
                    .uri("/projects/{projectIdOrPath}", projectIdOrPath)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (project != null) {
                String defaultBranch = (String) project.get("default_branch");
                log.debug("项目 {} 的默认分支: {}", projectIdOrPath, defaultBranch);
                return defaultBranch;
            }
            return null;
        } catch (Exception e) {
            log.error("获取默认分支失败: {}", projectIdOrPath, e);
            return null;
        }
    }

    // ==================== 提交相关 ====================

    /**
     * 获取项目提交历史
     *
     * @param projectIdOrPath 项目ID或路径
     * @param refName 分支名称或tag（可选）
     * @param perPage 每页数量（可选，默认20）
     * @param page 页码（可选，默认1）
     * @return 提交列表
     */
    public List<GitlabCommit> getCommits(String projectIdOrPath, String refName, Integer perPage, Integer page) {
        try {
            log.debug("获取提交历史: {}, branch: {}", projectIdOrPath, refName);

            List<GitlabCommit> commits = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{projectIdOrPath}/repository/commits")
                            .queryParamIfPresent("ref_name", Optional.ofNullable(refName))
                            .queryParamIfPresent("per_page", Optional.ofNullable(perPage))
                            .queryParamIfPresent("page", Optional.ofNullable(page))
                            .build(projectIdOrPath))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GitlabCommit>>() {})
                    .block();

            log.debug("项目 {} 获取到 {} 条提交记录", projectIdOrPath, commits != null ? commits.size() : 0);
            return commits;
        } catch (Exception e) {
            log.error("获取提交历史失败: {}", projectIdOrPath, e);
            return List.of();
        }
    }

    /**
     * 获取单个提交详情
     *
     * @param projectIdOrPath 项目ID或路径
     * @param sha 提交SHA
     * @return 提交详情
     */
    public GitlabCommit getCommit(String projectIdOrPath, String sha) {
        try {
            log.debug("获取提交详情: {}, sha: {}", projectIdOrPath, sha);

            GitlabCommit commit = webClient.get()
                    .uri("/projects/{projectIdOrPath}/repository/commits/{sha}", projectIdOrPath, sha)
                    .retrieve()
                    .bodyToMono(GitlabCommit.class)
                    .block();

            return commit;
        } catch (Exception e) {
            log.error("获取提交详情失败: {}, sha: {}", projectIdOrPath, sha, e);
            return null;
        }
    }

    /**
     * 获取提交的文件差异
     *
     * @param projectIdOrPath 项目ID或路径
     * @param sha 提交SHA
     * @return 文件差异列表
     */
    public List<GitlabDiff> getCommitDiff(String projectIdOrPath, String sha) {
        try {
            log.debug("获取提交差异: {}, sha: {}", projectIdOrPath, sha);

            List<GitlabDiff> diffs = webClient.get()
                    .uri("/projects/{projectIdOrPath}/repository/commits/{sha}/diff", projectIdOrPath, sha)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GitlabDiff>>() {})
                    .block();

            return diffs;
        } catch (Exception e) {
            log.error("获取提交差异失败: {}, sha: {}", projectIdOrPath, sha, e);
            return List.of();
        }
    }

    // ==================== 合并请求相关 ====================

    /**
     * 获取合并请求列表
     *
     * @param projectIdOrPath 项目ID或路径
     * @param state 状态：opened, closed, locked, merged（可选）
     * @param perPage 每页数量（可选）
     * @param page 页码（可选）
     * @return 合并请求列表
     */
    public List<GitlabMergeRequest> getMergeRequests(String projectIdOrPath, String state, Integer perPage, Integer page) {
        try {
            log.debug("获取合并请求: {}, state: {}", projectIdOrPath, state);

            List<GitlabMergeRequest> mrs = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{projectIdOrPath}/merge_requests")
                            .queryParamIfPresent("state", Optional.ofNullable(state))
                            .queryParamIfPresent("per_page", Optional.ofNullable(perPage))
                            .queryParamIfPresent("page", Optional.ofNullable(page))
                            .build(projectIdOrPath))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GitlabMergeRequest>>() {})
                    .block();

            log.debug("项目 {} 获取到 {} 个合并请求", projectIdOrPath, mrs != null ? mrs.size() : 0);
            return mrs;
        } catch (Exception e) {
            log.error("获取合并请求失败: {}", projectIdOrPath, e);
            return List.of();
        }
    }

    /**
     * 获取单个合并请求详情
     *
     * @param projectIdOrPath 项目ID或路径
     * @param mergeRequestIid 合并请求IID
     * @return 合并请求详情
     */
    public GitlabMergeRequest getMergeRequest(String projectIdOrPath, Integer mergeRequestIid) {
        try {
            log.debug("获取合并请求详情: {}, iid: {}", projectIdOrPath, mergeRequestIid);

            GitlabMergeRequest mr = webClient.get()
                    .uri("/projects/{projectIdOrPath}/merge_requests/{iid}", projectIdOrPath, mergeRequestIid)
                    .retrieve()
                    .bodyToMono(GitlabMergeRequest.class)
                    .block();

            return mr;
        } catch (Exception e) {
            log.error("获取合并请求详情失败: {}, iid: {}", projectIdOrPath, mergeRequestIid, e);
            return null;
        }
    }

    /**
     * 获取合并请求的文件变更
     *
     * @param projectIdOrPath 项目ID或路径
     * @param mergeRequestIid 合并请求IID
     * @return 文件变更列表
     */
    public List<GitlabFileChange> getMergeRequestChanges(String projectIdOrPath, Integer mergeRequestIid) {
        try {
            log.debug("获取合并请求变更: {}, iid: {}", projectIdOrPath, mergeRequestIid);

            Map<String, Object> response = webClient.get()
                    .uri("/projects/{projectIdOrPath}/merge_requests/{iid}/changes", projectIdOrPath, mergeRequestIid)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.containsKey("changes")) {
                List<GitlabFileChange> changes = (List<GitlabFileChange>) response.get("changes");
                return changes;
            }
            return List.of();
        } catch (Exception e) {
            log.error("获取合并请求变更失败: {}, iid: {}", projectIdOrPath, mergeRequestIid, e);
            return List.of();
        }
    }

    /**
     * 获取合并请求的提交
     *
     * @param projectIdOrPath 项目ID或路径
     * @param mergeRequestIid 合并请求IID
     * @return 提交列表
     */
    public List<GitlabCommit> getMergeRequestCommits(String projectIdOrPath, Integer mergeRequestIid) {
        try {
            log.debug("获取合并请求提交: {}, iid: {}", projectIdOrPath, mergeRequestIid);

            List<GitlabCommit> commits = webClient.get()
                    .uri("/projects/{projectIdOrPath}/merge_requests/{iid}/commits", projectIdOrPath, mergeRequestIid)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GitlabCommit>>() {})
                    .block();

            return commits;
        } catch (Exception e) {
            log.error("获取合并请求提交失败: {}, iid: {}", projectIdOrPath, mergeRequestIid, e);
            return List.of();
        }
    }

    // ==================== 仓库变更相关 ====================

    /**
     * 获取仓库变更摘要（比较两个分支或提交）
     *
     * @param projectIdOrPath 项目ID或路径
     * @param from 起始分支或提交SHA
     * @param to 目标分支或提交SHA
     * @return 变更差异列表
     */
    public List<GitlabDiff> getRepositoryDiff(String projectIdOrPath, String from, String to) {
        try {
            log.debug("获取仓库变更: {} from {} to {}", projectIdOrPath, from, to);

            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/projects/{projectIdOrPath}/repository/compare")
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build(projectIdOrPath))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.containsKey("diffs")) {
                return (List<GitlabDiff>) response.get("diffs");
            }
            return List.of();
        } catch (Exception e) {
            log.error("获取仓库变更失败: {} from {} to {}", projectIdOrPath, from, to, e);
            return List.of();
        }
    }

    /**
     * 获取最近的代码变更（用于Agent分析）
     *
     * @param projectIdOrPath 项目ID或路径
     * @param branch 分支名称（可选，默认为默认分支）
     * @param days 获取最近几天的变更（默认7天）
     * @return 格式化的变更摘要
     */
    public String getRecentChangesSummary(String projectIdOrPath, String branch, Integer days) {
        try {
            if (branch == null) {
                branch = getDefaultBranch(projectIdOrPath);
            }
            if (branch == null) {
                branch = "main";
            }

            int limit = days != null ? days : 7;
            List<GitlabCommit> commits = getCommits(projectIdOrPath, branch, 50, 1);

            StringBuilder summary = new StringBuilder();
            summary.append(String.format("# %s 最近%d天的代码变更\n\n", projectIdOrPath, limit));

            if (commits == null || commits.isEmpty()) {
                summary.append("暂无变更记录\n");
                return summary.toString();
            }

            summary.append(String.format("共找到 %d 条提交记录\n\n", commits.size()));

            for (GitlabCommit commit : commits) {
                summary.append(String.format("## %s\n", commit.getShortId()));
                summary.append(String.format("**作者:** %s\n", commit.getAuthorName()));
                summary.append(String.format("**时间:** %s\n", commit.getCreatedAt()));
                summary.append(String.format("**标题:** %s\n", commit.getTitle()));

                if (commit.getStats() != null) {
                    summary.append(String.format("**变更:** +%d -%d (%d行)\n",
                            commit.getStats().getAdditions(),
                            commit.getStats().getDeletions(),
                            commit.getStats().getTotal()));
                }

                summary.append(String.format("**链接:** %s\n\n", commit.getWebUrl()));
            }

            return summary.toString();
        } catch (Exception e) {
            log.error("获取变更摘要失败: {}", projectIdOrPath, e);
            return "获取变更摘要失败: " + e.getMessage();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 测试连接
     *
     * @return 是否连接成功
     */
    public boolean testConnection() {
        try {
            String result = webClient.get()
                    .uri("/user")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("GitLab连接测试成功");
            return result != null;
        } catch (Exception e) {
            log.error("GitLab连接测试失败", e);
            return false;
        }
    }
}