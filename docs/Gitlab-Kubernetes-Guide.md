# GitLab 与 Kubernetes 集成说明（实现与使用）

本文梳理项目中与 GitLab、Kubernetes 相关的代码逻辑、配置和使用方法，便于快速对接 CI/CD 与集群运维场景。

## 1. 功能概览
- GitLab：通过 WebClient 调用 GitLab REST API，提供分支/提交/MR 查询、差异分析等能力，暴露 REST 接口并封装为 Agent 工具。
- Kubernetes：通过 `kubectl proxy` 暴露的 API Server，以 WebClient 直接访问，支持集群资源查询、Pod 日志/诊断、节点/Service/Deployment 列表与详情等，并作为 Agent 工具调用。

## 2. 配置
- 配置文件：`/Users/mark/Downloads/exp/knowledge/src/main/resources/application.yaml`
- 关键段：
```yaml
gitlab:
  url: http://gitlab.example.com/          # GitLab 基础 URL（末尾不要带 api/v4）
  token: your-personal-access-token        # 访问令牌
  api-version: api/v4

kubernetes:
  api-url: http://localhost:8001           # 建议通过 kubectl proxy 暴露
  api-version: api/v1
  default-namespace: default
  read-timeout: 30
```
- 部署时可通过 K8s Secret 注入 GitLab Token：`/Users/mark/Downloads/exp/knowledge/k8s/secret.yaml`（`gitlab-token`）。

## 3. GitLab 集成
### 3.1 代码结构
- 配置：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/gitlab/config/GitlabConfig.java`
- 核心客户端：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/gitlab/GitlabUtil.java`
  - 基于 `WebClient`，自动附带 `Authorization: Bearer <token>`
  - 封装项目/分支/提交/MR/差异/仓库对比等 API 调用
- Agent 工具：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/agent/service/GitlabService.java`
  - 方法均带 `@Tool`，可被 LLM 直接调用
- HTTP 接口：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/agent/controller/GitlabController.java`
  - 路由前缀 `/api/gitlab/*`，便于前端或外部系统集成

### 3.2 已实现能力
- 连接探测：`testGitlabConnection`
- 项目：获取项目 ID、默认分支、分支列表
- 提交：列表、详情、diff、最近变更摘要
- MR：列表、详情、变更文件、提交列表、分析摘要
- 仓库对比：`getRepositoryDiff`

### 3.3 使用方式
1) 配置 Token 与 URL（见第 2 节）。
2) 直接调用 REST（示例）：
   - 获取分支列表  
     `GET http://localhost:8080/api/gitlab/project/branches?project=group%2Fproject`
   - MR 详情  
     `GET http://localhost:8080/api/gitlab/project/merge-request?project=group%2Fproject&iid=42`
3) 在 Agent 对话中调用（需在对话中明确 GitLab 任务）：  
   “查询 `group/project` 最近 5 个提交并给我标题摘要”  
   Agent 会调用 `getCommitHistory` 并返回格式化结果。

## 4. Kubernetes 集成
### 4.1 代码结构
- 配置：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/k8s/config/KubernetesConfig.java`
- 核心客户端：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/k8s/KubernetesUtil.java`
  - 依赖 `kubectl proxy` 或直连 API Server，使用 JsonNode 解析
- Agent 工具：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/agent/service/KubernetesService.java`
  - 方法带 `@Tool`，可被 Agent 自动选用
- 参考文档：`/Users/mark/Downloads/exp/knowledge/docs/Kubernetes-Agent-Implementation.md`
- 部署脚本/清单：`/Users/mark/Downloads/exp/knowledge/k8s/`（`deploy.sh`、`deployment.yaml`、`configmap.yaml`、`ingress.yaml` 等）

### 4.2 预置能力（KubernetesService）
- 连接测试：`testKubernetesConnection`
- Namespace：`listNamespaces`
- Pod：`listPods`、`getPodDetails`、`getPodLogs`、`analyzePodIssue`
- Node：`listNodes`、`getNodeDetails`
- Service：`listServices`、`getServiceDetails`
- Deployment：`listDeployments`、`getDeploymentDetails`
- 事件：`listEvents`
- 健康检查：`healthCheck`

### 4.3 使用方式
1) 启动代理：`kubectl proxy --port=8001`
2) 确认 `application.yaml` 中 `kubernetes.api-url` 指向 proxy 地址。
3) Agent 对话示例：  
   - “测试 Kubernetes 连接”  
   - “列出 knowledge 命名空间的 Pod”  
   - “分析 Pod knowledge-0 为什么重启”  
   - “查看 Deployment knowledge 的镜像和副本数”
4) 脚本部署（可选）：  
   ```bash
   cd k8s
   chmod +x deploy.sh
   ./deploy.sh          # 交互式创建 namespace/secret/configmap/deployment
   ```
   需要重新拉镜像时可用 `redeploy.sh` 滚动更新。

## 5. Agent 集成与路由
- 核心编排：`/Users/mark/Downloads/exp/knowledge/src/main/java/com/mark/knowledge/agent/service/AgenticService.java`
  - 工具注入顺序：向量检索、通用计算、金融计算、MCP 文件、Kubernetes
  - 模型由 `ModelRouterService` 依据百分比或业务类型在本地 Ollama 与云端 DashScope 之间切换。
- GitLab 工具主要经 REST & @Tool 暴露，若需在统一 Agent 中默认启用，可在 `AgenticService` 的 `AiServices.builder(...).tools(...)` 列表中追加 `gitlabService`（目前未默认加入，按需扩展）。

## 6. 常见问题与排查
- GitLab 401/403：确认 Token 权限（api/read_api），或检查 `gitlab.url` 是否包含 `/api/v4`（应在 `api-version` 字段配置，不要重复）。
- GitLab SSL 自签：可在启动参数添加 `-Djavax.net.ssl.trustStore` 指向受信任证书，或在 WebClient builder 上自定义 `HttpClient`（需代码调整）。
- Kubernetes 连接失败：确认已运行 `kubectl proxy`，且 `api-url` 与端口匹配；集群 RBAC 权限不足会导致 403。
- Pod 日志为空：Pod 不存在或已被清理，先用 `listPods` 确认名称；若是多容器 Pod，可扩展日志接口传入 `container` 参数（当前默认第一个容器）。

## 7. 二次扩展建议
- GitLab：增加创建 MR、触发 Pipeline、下载 Artifact 的工具方法，或在 `GitlabController` 暴露写操作（注意权限与审计）。
- Kubernetes：补充伸缩/重启/滚动发布工具（`kubectl rollout restart` 等），或新增端点供 GitLab CI 调用实现自助发布。
- 安全：生产环境用 K8s Secret 注入令牌，并在 ConfigMap 中只保留非敏感配置；限制 `kubectl proxy` 仅本机或内网可访问。
