#!/bin/bash
set -e

echo "🔄 重新构建镜像并部署..."

# 构建新镜像
echo "1️⃣ 构建镜像..."
docker build -t knowledge:latest -f Dockerfile ..

# 更新 deployment
echo "2️⃣ 更新 K8s 部署..."
kubectl set image deployment/knowledge knowledge=knowledge:latest -n knowledge

# 等待 rollout 完成
echo "3️⃣ 等待部署完成..."
kubectl rollout status deployment/knowledge -n knowledge

echo "✅ 部署完成！"
echo ""
echo "查看 Pod 状态:"
kubectl get pods -n knowledge
