package com.mark.knowledge.gitlab.controller;

import com.mark.knowledge.gitlab.config.GitlabConfig;
import com.mark.knowledge.gitlab.dto.CodeQualityAnalysis;
import com.mark.knowledge.gitlab.dto.GitlabWebhookEvent;
import com.mark.knowledge.gitlab.entity.MRCodeAnalysis;
import com.mark.knowledge.gitlab.repository.MRCodeAnalysisRepository;
import com.mark.knowledge.gitlab.service.CodeQualityAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * GitLab Webhook 和代码质量分析 API
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/gitlab")
@Slf4j
public class GitlabWebhookController {

    private final CodeQualityAnalysisService analysisService;
    private final MRCodeAnalysisRepository analysisRepository;
    private final GitlabConfig gitlabConfig;

    public GitlabWebhookController(CodeQualityAnalysisService analysisService,
                                     MRCodeAnalysisRepository analysisRepository,
                                     GitlabConfig gitlabConfig) {
        this.analysisService = analysisService;
        this.analysisRepository = analysisRepository;
        this.gitlabConfig = gitlabConfig;
    }

    /**
     * GitLab Webhook 接收端点
     *
     * 在 GitLab 项目设置中配置：
     * URL: http://your-host/api/gitlab/webhook
     * Secret token: (可选)
     * 触发事件: Merge request events
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody GitlabWebhookEvent event,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token) {

        // 验证 Webhook Token
        if (gitlabConfig.getWebhookToken() != null &&
                !gitlabConfig.getWebhookToken().isEmpty()) {
            if (!gitlabConfig.getWebhookToken().equals(token)) {
                log.warn("Webhook Token 验证失败");
                return ResponseEntity.status(401).body(Map.of(
                        "status", "error",
                        "message", "Invalid webhook token"
                ));
            }
        }

        log.info("收到 GitLab Webhook: eventType={}, action={}",
                event.getObjectKind(),
                event.getMergeRequest() != null ? event.getMergeRequest().getAction() : "N/A");

        try {
            // 处理 MR 事件
            if ("merge_request".equals(event.getObjectKind())) {
                handleMergeRequestEvent(event);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Webhook processed successfully"
            ));

        } catch (Exception e) {
            log.error("处理 Webhook 失败", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 手动触发 MR 分析
     */
    @PostMapping("/analyze/{projectPath}/merge-request/{iid}")
    public ResponseEntity<Map<String, Object>> analyzeMergeRequest(
            @PathVariable String projectPath,
            @PathVariable Integer iid) {

        log.info("手动触发分析: {} MR {}", projectPath, iid);

        // 异步执行分析
        CompletableFuture.runAsync(() -> {
            try {
                CodeQualityAnalysis analysis = analysisService.analyzeMergeRequest(projectPath, iid);
                saveAnalysis(analysis);
                log.info("分析完成: {} MR {}", projectPath, iid);
            } catch (Exception e) {
                log.error("分析失败: {} MR {}", projectPath, iid, e);
            }
        });

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "分析已启动，请稍后查看结果"
        ));
    }

    /**
     * 获取 MR 分析结果
     */
    @GetMapping("/analysis/{projectPath}/merge-request/{iid}")
    public ResponseEntity<MRCodeAnalysis> getAnalysis(
            @PathVariable String projectPath,
            @PathVariable Integer iid) {

        return analysisRepository.findByProjectPathAndMergeRequestIid(projectPath, iid)
                .map(analysis -> ResponseEntity.ok(analysis))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取项目的所有分析记录
     */
    @GetMapping("/analysis/{projectPath}")
    public ResponseEntity<List<MRCodeAnalysis>> getProjectAnalyses(@PathVariable String projectPath) {
        return ResponseEntity.ok(analysisRepository.findByProjectPathOrderByCreatedAtDesc(projectPath));
    }

    /**
     * 获取高风险 MR
     */
    @GetMapping("/analysis/high-risk")
    public ResponseEntity<List<MRCodeAnalysis>> getHighRiskAnalyses() {
        return ResponseEntity.ok(analysisRepository.findHighRiskAnalyses());
    }

    /**
     * 获取项目风险统计
     */
    @GetMapping("/analysis/{projectPath}/stats")
    public ResponseEntity<Map<String, Object>> getProjectStats(@PathVariable String projectPath) {
        List<Object[]> riskCounts = analysisRepository.countRiskByProject(projectPath);

        Map<String, Integer> stats = Map.of(
                "LOW", 0,
                "MEDIUM", 0,
                "HIGH", 0,
                "CRITICAL", 0
        );

        for (Object[] row : riskCounts) {
            String level = (String) row[0];
            Long count = (Long) row[1];
            stats.put(level, count.intValue());
        }

        return ResponseEntity.ok(Map.of(
                "projectPath", projectPath,
                "riskDistribution", stats
        ));
    }

    /**
     * 处理 MR 事件
     */
    private void handleMergeRequestEvent(GitlabWebhookEvent event) {
        GitlabWebhookEvent.MergeRequestObject mr = event.getMergeRequest();
        if (mr == null) return;

        String projectPath = event.getProject().getPathWithNamespace();
        Integer iid = mr.getIid();
        String action = mr.getAction();

        log.info("MR 事件: {} MR {} - {}", projectPath, iid, action);

        // 只在 MR 打开或更新时触发分析
        if ("open".equals(action) || "update".equals(action)) {
            // 异步执行分析，避免阻塞 Webhook 响应
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000); // 等待 CI Pipeline 启动
                    CodeQualityAnalysis analysis = analysisService.analyzeMergeRequest(projectPath, iid);
                    saveAnalysis(analysis);
                    log.info("MR 分析完成: {} MR {}", projectPath, iid);
                } catch (Exception e) {
                    log.error("MR 分析失败: {} MR {}", projectPath, iid, e);

                    // 保存失败记录
                    MRCodeAnalysis errorRecord = new MRCodeAnalysis();
                    errorRecord.setProjectPath(projectPath);
                    errorRecord.setMergeRequestIid(iid);
                    errorRecord.setErrorMessage(e.getMessage());
                    errorRecord.setAuthor(mr.getAuthor() != null ? mr.getAuthor().getUsername() : "unknown");
                    errorRecord.setSourceBranch(mr.getSourceBranch());
                    errorRecord.setTargetBranch(mr.getTargetBranch());
                    errorRecord.setRiskLevel(CodeQualityAnalysis.RiskLevel.HIGH);
                    errorRecord.setAnalyzed(false);
                    errorRecord.setMrWebUrl(mr.getWebUrl());
                    analysisRepository.save(errorRecord);
                }
            });
        }
    }

    /**
     * 保存分析结果到数据库
     */
    private void saveAnalysis(CodeQualityAnalysis analysis) {
        MRCodeAnalysis entity = analysisRepository
                .findByProjectPathAndMergeRequestIid(analysis.getProjectPath(), analysis.getMergeRequestIid())
                .orElse(new MRCodeAnalysis());

        entity.setProjectPath(analysis.getProjectPath());
        entity.setMergeRequestIid(analysis.getMergeRequestIid());
        entity.setSourceBranch(analysis.getSourceBranch());
        entity.setTargetBranch(analysis.getTargetBranch());
        entity.setAuthor(analysis.getAuthor());
        entity.setSummary(analysis.getSummary());
        entity.setRiskLevel(analysis.getRisk() != null ? analysis.getRisk().getLevel() : null);
        entity.setRiskScore(analysis.getRisk() != null ? analysis.getRisk().getScore() : null);
        if (analysis.getRisk() != null && analysis.getRisk().getFactors() != null) {
            entity.setRiskFactors(String.join("; ", analysis.getRisk().getFactors()));
        }
        entity.setRecommendation(analysis.getRisk() != null ? analysis.getRisk().getRecommendation() : null);

        if (analysis.getPipelineStatus() != null) {
            entity.setPipelineStatus(analysis.getPipelineStatus().getStatus());
            entity.setPipelineStages(analysis.getPipelineStatus().getStages());
            entity.setPipelineWebUrl(analysis.getPipelineStatus().getWebUrl());
        }

        if (analysis.getChanges() != null) {
            entity.setAdditions(analysis.getChanges().getAdditions());
            entity.setDeletions(analysis.getChanges().getDeletions());
            entity.setTotalChanges(analysis.getChanges().getTotal());
        }

        entity.setAnalyzed(true);
        entity.setErrorMessage(null);

        analysisRepository.save(entity);
    }
}
