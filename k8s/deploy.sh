#!/bin/bash

# Kubernetes 部署脚本
# 用于部署 Knowledge 应用到本地 Docker Desktop Kubernetes

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}Knowledge 应用 Kubernetes 部署脚本${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""

# 检查 kubectl 是否安装
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}错误: kubectl 未安装${NC}"
    echo "请先安装 kubectl: https://kubernetes.io/docs/tasks/tools/"
    exit 1
fi

# 检查 Docker Desktop Kubernetes 是否运行
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}错误: Kubernetes 集群未运行${NC}"
    echo "请在 Docker Desktop 中启用 Kubernetes"
    exit 1
fi

echo -e "${GREEN}✓ Kubernetes 集群运行正常${NC}"
echo ""

# 步骤 1: 构建镜像
echo -e "${YELLOW}[1/5] 构建 Docker 镜像...${NC}"
docker build -t knowledge:latest -f ../Dockerfile ..
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 镜像构建成功${NC}"
else
    echo -e "${RED}✗ 镜像构建失败${NC}"
    exit 1
fi
echo ""

# 步骤 2: 创建命名空间
echo -e "${YELLOW}[2/5] 创建命名空间...${NC}"
kubectl apply -f namespace.yaml
echo -e "${GREEN}✓ 命名空间创建成功${NC}"
echo ""

# 步骤 3: 创建 PVC
echo -e "${YELLOW}[3/5] 创建持久化存储...${NC}"
kubectl apply -f persistent-volume-claim.yaml
echo -e "${GREEN}✓ 持久化存储创建成功${NC}"
echo ""

# 步骤 4: 创建 ConfigMap
echo -e "${YELLOW}[4/5] 创建配置文件...${NC}"
kubectl apply -f configmap.yaml
echo -e "${GREEN}✓ 配置文件创建成功${NC}"
echo ""

# 步骤 5: 创建 Secret
echo -e "${YELLOW}[5/7] 创建密钥...${NC}"
echo -e "${YELLOW}注意: 请根据实际情况修改 secret.yaml 中的敏感信息${NC}"
read -p "是否使用默认密钥配置？(y/n): " confirm_secret
if [ "$confirm_secret" = "y" ]; then
    kubectl apply -f secret.yaml
    echo -e "${GREEN}✓ 密钥创建成功${NC}"
else
    echo -e "${YELLOW}跳过密钥创建，请手动配置后重新执行 kubectl apply -f secret.yaml${NC}"
fi
echo ""

# 步骤 6: 部署应用
echo -e "${YELLOW}[6/7] 部署应用...${NC}"
kubectl apply -f deployment.yaml
echo -e "${GREEN}✓ 应用部署成功${NC}"
echo ""

# 步骤 7: 创建 Service
echo -e "${YELLOW}[7/7] 创建服务...${NC}"
kubectl apply -f service.yaml

# 可选: 创建 Ingress
read -p "是否部署 Ingress？(需要先安装 NGINX Ingress Controller) (y/n): " deploy_ingress
if [ "$deploy_ingress" = "y" ]; then
    kubectl apply -f ingress.yaml
    echo -e "${GREEN}✓ Ingress 创建成功${NC}"
fi

echo -e "${GREEN}✓ 服务创建成功${NC}"
echo ""

# 等待 Pod 启动
echo -e "${YELLOW}等待 Pod 启动...${NC}"
kubectl wait --for=condition=ready pod -l app=knowledge -n knowledge --timeout=300s || true

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}部署完成！${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""
echo "📝 外部服务说明："
echo "  - Qdrant: 请确保在宿主机运行，访问地址 http://host.docker.internal:6333"
echo "  - Ollama: 请确保在宿主机运行，访问地址 http://host.docker.internal:11434"
echo ""
echo "查看部署状态:"
echo "  kubectl get pods -n knowledge"
echo "  kubectl get services -n knowledge"
echo ""
echo "查看日志:"
echo "  kubectl logs -f deployment/knowledge -n knowledge"
echo ""
echo "访问应用:"
echo "  - NodePort: http://localhost:30080"
echo "  - Port Forward: kubectl port-forward svc/knowledge 8080:8080 -n knowledge"
echo "  - Ingress: http://knowledge.local (需要配置 /etc/hosts)"
echo ""
echo "删除部署:"
echo "  kubectl delete namespace knowledge"
