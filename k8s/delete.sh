#!/bin/bash

# Kubernetes 清理脚本
# 删除 Knowledge 应用的所有资源

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}Knowledge 应用清理脚本${NC}"
echo -e "${YELLOW}======================================${NC}"
echo ""

read -p "确定要删除所有资源吗？数据将丢失！(yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "取消删除"
    exit 0
fi

echo -e "${YELLOW}删除资源...${NC}"

kubectl delete namespace knowledge

echo -e "${GREEN}✓ 所有资源已删除${NC}"
echo ""
echo "PVC 数据可能仍然存在，如需彻底清理:"
echo "  kubectl delete pvc -n knowledge --all"
