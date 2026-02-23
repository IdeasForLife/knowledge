# Knowledge 应用 Kubernetes 部署指南

本文档说明如何将 Knowledge 应用部署到 Docker Desktop 的 Kubernetes 集群。

## 前提条件

1. **Docker Desktop** 已安装并启用 Kubernetes
2. **kubectl** 已安装并配置
3. **Docker** 镜像构建功能可用

## 快速开始

### 1. 构建镜像并部署

```bash
cd k8s
chmod +x deploy.sh
./deploy.sh
```

### 2. 验证部署

```bash
# 查看 Pod 状态
kubectl get pods -n knowledge

# 查看服务
kubectl get services -n knowledge

# 查看日志
kubectl logs -f deployment/knowledge -n knowledge
```

### 3. 访问应用

有三种方式访问应用：

#### 方式 1: NodePort (推荐用于本地测试)
```bash
# 直接访问
curl http://localhost:30080

# 浏览器访问
open http://localhost:30080
```

#### 方式 2: Port Forward
```bash
kubectl port-forward svc/knowledge 8080:8080 -n knowledge

# 访问 http://localhost:8080
```

#### 方式 3: Ingress (需要先安装 NGINX Ingress Controller)  可选

1. 安装 NGINX Ingress Controller:
```bash
# Docker Desktop Kubernetes
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml
```

2. 配置 hosts:
```bash
echo "127.0.0.1 knowledge.local" | sudo tee -a /etc/hosts
```

3. 访问 http://knowledge.local

## 配置说明

### ConfigMap 配置

修改 `configmap.yaml` 来调整应用配置：

```yaml
# Ollama 服务地址
OLLAMA_BASE_URL: "http://ollama:11434"

# 模型配置
OLLAMA_CHAT_MODEL: "qwen2.5:7b"
OLLAMA_EMBEDDING_MODEL: "qwen3-embedding:0.6b"

# Qdrant 向量数据库
QDRANT_HOST: "qdrant"
QDRANT_PORT: "6334"
```

### Secret 配置

修改 `secret.yaml` 来设置敏感信息：

```yaml
stringData:
  dashscope-api-key: "your-actual-api-key"
  gitlab-token: "your-actual-gitlab-token"
```

**更新 Secret:**
```bash
kubectl apply -f secret.yaml
# 重启 Pod 使配置生效
kubectl rollout restart deployment/knowledge -n knowledge
```

## 常用命令

### 查看 Pod 状态
```bash
kubectl get pods -n knowledge
kubectl describe pod <pod-name> -n knowledge
```

### 查看日志
```bash
# 查看应用日志
kubectl logs -f deployment/knowledge -n knowledge

# 查看所有容器日志
kubectl logs -f deployment/knowledge -n knowledge --all-containers=true
```

### 进入容器
```bash
kubectl exec -it <pod-name> -n knowledge -- /bin/bash
```

### 扩缩容
```bash
# 扩展到 3 个副本
kubectl scale deployment/knowledge --replicas=3 -n knowledge

# 查看扩容状态
kubectl get pods -n knowledge -w
```

### 更新镜像
```bash
# 构建新镜像
docker build -t knowledge:v1.0.1 -f ../Dockerfile ..

# 更新部署
kubectl set image deployment/knowledge knowledge=knowledge:v1.0.1 -n knowledge

# 或编辑 deployment
kubectl edit deployment/knowledge -n knowledge
```

### 回滚部署
```bash
# 查看历史
kubectl rollout history deployment/knowledge -n knowledge

# 回滚到上一版本
kubectl rollout undo deployment/knowledge -n knowledge

# 回滚到指定版本
kubectl rollout undo deployment/knowledge --to-revision=2 -n knowledge
```

## 资源限制

默认资源配置：

| 组件 | CPU Request | CPU Limit | Memory Request | Memory Limit |
|------|-------------|-----------|----------------|--------------|
| knowledge | 250m | 1000m | 512Mi | 2Gi |
| qdrant | 100m | 500m | 256Mi | 1Gi |
| ollama | 1000m | 4000m | 2Gi | 8Gi |

可根据实际情况在 `deployment.yaml` 中调整。

## 持久化存储

数据存储路径：

| 组件 | PVC 名称 | 挂载路径 | 用途 |
|------|----------|----------|------|
| knowledge | knowledge-data-pvc | /app/data | SQLite 数据库、上传文件 |
| qdrant | qdrant-data-pvc | /qdrant/storage | 向量数据 |
| ollama | ollama-data-pvc | /root/.ollama | 模型文件 |

## 监控和健康检查

### 健康检查端点
- Liveness: `/actuator/health`
- Readiness: `/actuator/health`

### 查看健康状态
```bash
kubectl describe pod <pod-name> -n knowledge | grep -A 10 Liveness
```

## 故障排查

### Pod 无法启动
```bash
# 查看 Pod 事件
kubectl describe pod <pod-name> -n knowledge

# 查看日志
kubectl logs <pod-name> -n knowledge
```

### 无法连接服务
```bash
# 检查 Service
kubectl get endpoints -n knowledge

# 测试服务连通性
kubectl run -it --rm debug --image=busybox --restart=Never -- sh -c "nc -zv knowledge 8080"
```

### 存储问题
```bash
# 查看 PVC 状态
kubectl get pvc -n knowledge

# 查看 PV
kubectl get pv
```

## 清理环境

```bash
# 删除所有资源
cd k8s
chmod +x delete.sh
./delete.sh

# 或手动删除
kubectl delete namespace knowledge
```

## 架构说明

```
┌─────────────────┐
│   Ingress       │ (可选)
└────────┬────────┘
         │
┌────────▼────────┐
│  knowledge      │ ← 应用服务
│  (8080)         │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
┌───▼───┐ ┌──▼─────┐
│qdrant│ │ollama  │
│6333  │ │11434   │
└──────┘ └────────┘
```

## 生产环境建议

1. **使用外部服务**: 将 Qdrant 和 Ollama 部署为独立服务
2. **配置资源自动伸缩**: 使用 HPA (Horizontal Pod Autoscaler)
3. **使用外部密钥管理**: 集成 HashiCorp Vault 或云密钥服务
4. **启用持久化**: 使用分布式存储类（如 Longhorn）
5. **配置监控**: 部署 Prometheus + Grafana
6. **配置日志收集**: 部署 ELK 或 Loki

## 参考资料

- [Kubernetes 官方文档](https://kubernetes.io/docs/)
- [Docker Desktop Kubernetes](https://docs.docker.com/desktop/kubernetes/)
- [kubectl 命令指南](https://kubernetes.io/docs/reference/kubectl/)
