package com.mark.knowledge.agent.controller;

import com.mark.knowledge.agent.service.GitlabService;
import com.mark.knowledge.gitlab.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GitLab REST API控制器
 *
 * 提供HTTP接口访问GitLab功能，便于前端调用或其他服务集成
 * # 1. 测试连接
 * http://localhost:8080/api/gitlab/test
 *
 * # 2. 获取项目ID
 * http://localhost:8080/api/gitlab/project/id?project=root%2Flangchain4j-for-beginners-main
 *
 * # 3. 获取分支列表
 * http://localhost:8080/api/gitlab/project/branches?project=root%2Flangchain4j-for-beginners-main
 *
 * # 4. 获取默认分支
 * http://localhost:8080/api/gitlab/project/default-branch?project=root%2Flangchain4j-for-beginners-main
 *
 * # 5. 获取提交历史（默认）
 * http://localhost:8080/api/gitlab/project/commits?project=root%2Flangchain4j-for-beginners-main
 *
 * # 5a. 指定分支和数量（示例：main 分支，10条）
 * http://localhost:8080/api/gitlab/project/commits?project=root%2Flangchain4j-for-beginners-main&branch=main&limit=10
 *
 * # 6. 获取提交详情（替换 a1b2c3d4 为实际 SHA）
 * http://localhost:8080/api/gitlab/project/commit?project=root%2Flangchain4j-for-beginners-main&sha=a1b2c3d4
 *
 * # 7. 获取提交差异
 * http://localhost:8080/api/gitlab/project/commit/diff?project=root%2Flangchain4j-for-beginners-main&sha=a1b2c3d4
 *
 * # 8. 获取合并请求列表（默认 opened）
 * http://localhost:8080/api/gitlab/project/merge-requests?project=root%2Flangchain4j-for-beginners-main
 *
 * # 8a. 获取所有状态 MR，最多5个
 * http://localhost:8080/api/gitlab/project/merge-requests?project=root%2Flangchain4j-for-beginners-main&state=all&limit=5
 *
 * # 9. 获取 MR 详情（替换 42 为实际 IID）
 * http://localhost:8080/api/gitlab/project/merge-request?project=root%2Flangchain4j-for-beginners-main&iid=42
 *
 * # 10. 获取 MR 变更文件
 * http://localhost:8080/api/gitlab/project/merge-request/changes?project=root%2Flangchain4j-for-beginners-main&iid=42
 *
 * # 11. 获取 MR 提交历史
 * http://localhost:8080/api/gitlab/project/merge-request/commits?project=root%2Flangchain4j-for-beginners-main&iid=42
 *
 * # 12. 分析 MR
 * http://localhost:8080/api/gitlab/project/merge-request/analyze?project=root%2Flangchain4j-for-beginners-main&iid=42
 *
 * # 13. 最近代码变更（默认7天）
 * http://localhost:8080/api/gitlab/project/recent-changes?project=root%2Flangchain4j-for-beginners-main
 *
 * # 13a. 指定分支和天数（示例：develop 分支，最近3天）
 * http://localhost:8080/api/gitlab/project/recent-changes?project=root%2Flangchain4j-for-beginners-main&branch=develop&days=3
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/gitlab")
public class GitlabController {

    private final GitlabService gitlabService;

    public GitlabController(GitlabService gitlabService) {
        this.gitlabService = gitlabService;
    }

    /**
     * 测试GitLab连接
     */
    @GetMapping("/test")
    public String testConnection() {
        return gitlabService.testGitlabConnection();
    }

    /**
     * 获取项目ID
     */
    @GetMapping("/project/id")
    public String getProjectId(@RequestParam String project) {
        return gitlabService.getProjectId(project);
    }

    /**
     * 获取项目分支列表
     */
    @GetMapping("/project/branches")
    public String getBranches(@RequestParam String project) {
        return gitlabService.getProjectBranches(project);
    }

    /**
     * 获取默认分支
     */
    @GetMapping("/project/default-branch")
    public String getDefaultBranch(@RequestParam String project) {
        return gitlabService.getDefaultBranch(project);
    }

    /**
     * 获取提交历史
     */
    @GetMapping("/project/commits")
    public String getCommits(
            @RequestParam String project,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) Integer limit) {
        return gitlabService.getCommitHistory(project, branch, limit);
    }

    /**
     * 获取提交详情
     */
    @GetMapping("/project/commit")
    public String getCommitDetails(
            @RequestParam String project,
            @RequestParam String sha) {
        return gitlabService.getCommitDetails(project, sha);
    }

    /**
     * 获取提交差异
     */
    @GetMapping("/project/commit/diff")
    public String getCommitDiff(
            @RequestParam String project,
            @RequestParam String sha) {
        return gitlabService.getCommitDiff(project, sha);
    }

    /**
     * 获取合并请求列表
     */
    @GetMapping("/project/merge-requests")
    public String getMergeRequests(
            @RequestParam String project,
            @RequestParam(required = false, defaultValue = "opened") String state,
            @RequestParam(required = false) Integer limit) {
        return gitlabService.getMergeRequests(project, state, limit);
    }

    /**
     * 获取合并请求详情
     */
    @GetMapping("/project/merge-request")
    public String getMergeRequestDetails(
            @RequestParam String project,
            @RequestParam Integer iid) {
        return gitlabService.getMergeRequestDetails(project, iid);
    }

    /**
     * 获取合并请求变更
     */
    @GetMapping("/project/merge-request/changes")
    public String getMergeRequestChanges(
            @RequestParam String project,
            @RequestParam Integer iid) {
        return gitlabService.getMergeRequestChanges(project, iid);
    }

    /**
     * 获取合并请求提交历史
     */
    @GetMapping("/project/merge-request/commits")
    public String getMergeRequestCommits(
            @RequestParam String project,
            @RequestParam Integer iid) {
        return gitlabService.getMergeRequestCommits(project, iid);
    }

    /**
     * 分析合并请求
     */
    @GetMapping("/project/merge-request/analyze")
    public String analyzeMergeRequest(
            @RequestParam String project,
            @RequestParam Integer iid) {
        return gitlabService.analyzeMergeRequest(project, iid);
    }

    /**
     * 获取最近代码变更
     */
    @GetMapping("/project/recent-changes")
    public String getRecentChanges(
            @RequestParam String project,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) Integer days) {
        return gitlabService.getRecentCodeChanges(project, branch, days);
    }
}
