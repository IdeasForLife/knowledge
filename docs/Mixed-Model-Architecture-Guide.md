# 混合模型架构配置指南

本文档详细说明如何配置和使用阿里云DashScope + 本地Ollama的混合模型架构。

## 目录

- [架构概述](#架构概述)
- [快速配置](#快速配置)
- [路由策略详解](#路由策略详解)
- [监控和日志](#监控和日志)
- [常见问题](#常见问题)

---

## 架构概述

### 混合模型架构

```
┌─────────────┐
│  用户请求    │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│  模型路由服务      │
│  ModelRouter     │
└──────┬───────────┘
       │
       ├─────────────────┬─────────────────┐
       │                 │                 │
       ▼                 ▼                 ▼
  ┌─────────┐      ┌─────────┐      ┌─────────┐
  │ 百分比   │      │ 业务类型 │      │ 默认   │
  │ 路由    │      │ 路由    │      │ 路由   │
  └────┬────┘      └────┬────┘      └────┬────┘
       │                │                │
       ▼                ▼                ▼
  ┌─────────┐      ┌─────────┐      ┌─────────┐
  │阿里云   │      │本地Ollama│    │本地Ollama│
  │DashScope│      │qwen2.5:7b│    │qwen2.5:7b│
  └─────────┘      └─────────┘      └─────────┘
```

### 核心组件

1. **ChatModel** - 聊天模型接口
   - `OllamaChatModel` - 本地聊天模型
   - `OpenAiChatModel` - 阿里云聊天模型（OpenAI兼容接口）

2. **EmbeddingModel** - 嵌入模型（固定使用本地）
   - `OllamaEmbeddingModel` - 本地向量嵌入模型

3. **ModelRouterService** - 模型路由服务
   - 根据策略选择合适的模型
   - 支持百分比和业务类型两种路由策略

---

## 快速配置

### 步骤1：获取阿里云API Key

1. 访问 [阿里云DashScope控制台](https://dashscope.console.aliyun.com/)
2. 登录阿里云账号
3. 创建API Key
4. 保存API Key（只显示一次）

### 步骤2：配置环境变量

```bash
# Linux/Mac
export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# Windows CMD
set DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

或直接在 `application.yaml` 中配置（不推荐，因为会暴露密钥）：

```yaml
dashscope:
  api-key: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 步骤3：选择路由策略

#### 选项A：百分比路由（推荐新手）

```yaml
model-router:
  strategy: PERCENTAGE
  percentage:
    aliyun: 30  # 30%请求走阿里云
    local: 70   # 70%请求走本地
```

#### 选项B：业务类型路由（推荐进阶用户）

```yaml
model-router:
  strategy: BUSINESS_TYPE
  business-type:
    aliyun-types:
      - COMPLEX_QUERY     # 复杂查询
      - LONG_CONTEXT      # 长上下文
      - HIGH_PRECISION    # 高精度要求
    local-types:
      - SIMPLE_QA         # 简单问答
      - TOOL_CALLING      # 工具调用
      - GENERAL_CHAT      # 通用对话
```

### 步骤4：重启应用

```bash
mvn spring-boot:run
```

### 步骤5：验证配置

查看启动日志：

```log
==========================================
初始化Ollama聊天模型
  模型: qwen2.5:7b
  URL: http://localhost:11434
==========================================
初始化Ollama嵌入模型: qwen3-embedding:0.6b @ http://localhost:11434
==========================================
初始化阿里云DashScope聊天模型
  模型: qwen-plus
  URL: https://dashscope.aliyuncs.com/compatible-mode/v1
==========================================
```

查看请求日志：

```log
检测到业务类型: COMPLEX_QUERY, 使用模型: OpenAi
路由到阿里云模型 (随机值: 25 < 30%)
```

---

## 路由策略详解

### 百分比路由（PERCENTAGE）

#### 工作原理

系统生成0-100的随机数，根据配置的百分比阈值选择模型：

```
随机数 < aliyun百分比 → 使用阿里云模型
随机数 >= aliyun百分比 → 使用本地模型
```

#### 配置示例

```yaml
model-router:
  strategy: PERCENTAGE
  percentage:
    aliyun: 30  # 30%请求走阿里云
    local: 70   # 70%请求走本地
```

#### 日志示例

```log
路由到阿里云模型 (随机值: 25 < 30%)
路由到本地模型 (随机值: 75 >= 30%)
```

#### 适用场景

- ✅ 成本控制：固定API调用费用
- ✅ 负载均衡：分散请求压力
- ✅ A/B测试：比较两个模型效果
- ⚠️ 不关心问题类型：简单随机分配

#### 最佳实践

```yaml
# 开发环境：全部使用本地模型
model-router:
  strategy: PERCENTAGE
  percentage:
    aliyun: 0
    local: 100

# 测试环境：少量使用阿里云
model-router:
  strategy: PERCENTAGE
  percentage:
    aliyun: 10
    local: 90

# 生产环境：根据预算调整
model-router:
  strategy: PERCENTAGE
  percentage:
    aliyun: 30
    local: 70
```

---

### 业务类型路由（BUSINESS_TYPE）

#### 工作原理

系统分析用户输入的关键词和特征，自动判断业务类型，然后根据配置的路由表选择模型。

#### 业务类型定义

| 业务类型 | 说明 | 关键词特征 | 推荐模型 |
|---------|------|-----------|---------|
| COMPLEX_QUERY | 复杂查询 | 分析、比较、总结、推理 | 阿里云 |
| LONG_CONTEXT | 长上下文 | 输入长度 > 200字 | 阿里云 |
| HIGH_PRECISION | 高精度要求 | 评估、建议、方案 | 阿里云 |
| SIMPLE_QA | 简单问答 | 基本问题查询 | 本地 |
| TOOL_CALLING | 工具调用 | 计算、查询、搜索 | 本地 |
| GENERAL_CHAT | 通用对话 | 日常聊天 | 本地 |

#### 配置示例

```yaml
model-router:
  strategy: BUSINESS_TYPE
  business-type:
    aliyun-types:
      - COMPLEX_QUERY
      - LONG_CONTEXT
      - HIGH_PRECISION
    local-types:
      - SIMPLE_QA
      - TOOL_CALLING
      - GENERAL_CHAT
```

#### 日志示例

```log
检测到业务类型: COMPLEX_QUERY, 使用模型: OpenAi
业务类型 COMPLEX_QUERY 路由到阿里云模型
检测到业务类型: TOOL_CALLING, 使用模型: Ollama
业务类型 TOOL_CALLING 路由到本地模型
```

#### 适用场景

- ✅ 优化性能：问题类型与模型能力匹配
- ✅ 用户体验：复杂问题用更好的模型
- ✅ 成本优化：简单问题用免费本地模型
- ⚠️ 需要了解业务特点

#### 最佳实践

```yaml
# 金融分析场景：复杂查询用阿里云
model-router:
  strategy: BUSINESS_TYPE
  business-type:
    aliyun-types:
      - COMPLEX_QUERY     # 金融产品分析
      - HIGH_PRECISION    # 投资建议
    local-types:
      - TOOL_CALLING      # IRR计算等
      - SIMPLE_QA         # 基本查询

# 客服场景：全部本地，极速响应
model-router:
  strategy: BUSINESS_TYPE
  business-type:
    local-types:
      - COMPLEX_QUERY
      - LONG_CONTEXT
      - HIGH_PRECISION
      - SIMPLE_QA
      - TOOL_CALLING
      - GENERAL_CHAT
```

---

## 监控和日志

### 日志级别

```yaml
logging:
  level:
    com.mark.knowledge.chat.service.ModelRouterService: DEBUG
```

### 关键日志

```log
# 模型选择
DEBUG c.m.k.c.service.ModelRouterService - 路由到阿里云模型 (随机值: 25 < 30%)
DEBUG c.m.k.c.service.ModelRouterService - 检测到业务类型: COMPLEX_QUERY, 使用模型: OpenAi

# 模型使用
INFO  c.m.k.c.service.ChatService - 检测到业务类型: TOOL_CALLING, 使用模型: Ollama
```

### 监控指标

建议监控以下指标：

1. **模型使用比例**
   ```sql
   SELECT
     CASE WHEN log LIKE '%OpenAi%' THEN 'Aliyun' ELSE 'Local' END as model,
     COUNT(*) as requests
   FROM request_logs
   GROUP BY model;
   ```

2. **平均响应时间**
   ```sql
   SELECT
     CASE WHEN log LIKE '%OpenAi%' THEN 'Aliyun' ELSE 'Local' END as model,
     AVG(response_time) as avg_time
   FROM request_logs
   GROUP BY model;
   ```

3. **业务类型分布**
   ```sql
   SELECT
     business_type,
     COUNT(*) as count
   FROM request_logs
   GROUP BY business_type
   ORDER BY count DESC;
   ```

---

## 常见问题

### Q1: 如何临时禁用阿里云模型？

**方法1**：注释掉API Key配置
```yaml
# dashscope:
#   api-key: ${DASHSCOPE_API_KEY}
```

**方法2**：设置为默认值
```yaml
dashscope:
  api-key: your-api-key-here
```

**方法3**：修改百分比配置
```yaml
model-router:
  strategy: PERCENTAGE
  percentage:
    aliyun: 0
    local: 100
```

### Q2: 为什么所有请求都走本地模型？

可能原因：
1. 阿里云API Key未配置或配置错误
2. `dashscope.api-key` 仍为默认值 `your-api-key-here`
3. 网络连接问题

解决方法：
```bash
# 检查API Key是否配置
echo $DASHSCOPE_API_KEY

# 检查配置文件
grep "dashscope.api-key" src/main/resources/application.yaml

# 查看启动日志
# 如果看到 "阿里云模型未配置，使用本地模型"，说明配置有问题
```

### Q3: 百分比路由不准确？

百分比路由是**随机**的，不是严格的比例。在请求量较少时，实际比例可能偏离配置值。

**示例**：
- 配置：aliyun: 30%, local: 70%
- 前10个请求：可能 4个阿里云，6个本地（40%/60%）
- 前1000个请求：约 300个阿里云，700个本地（接近30%/70%）

**解决方法**：增加请求量，或使用业务类型路由进行精确控制。

### Q4: 如何自定义业务类型检测？

修改 `ModelRouterService.java` 中的检测方法：

```java
private boolean isComplexQuery(String input) {
    // 添加自定义关键词
    String[] complexKeywords = {
        "分析", "比较", "总结", "详细说明", "深入",
        "推理", "判断", "评估", "建议", "方案",
        "你的关键词1", "你的关键词2"  // 添加这里
    };

    for (String keyword : complexKeywords) {
        if (input.contains(keyword)) {
            return true;
        }
    }
    return false;
}
```

### Q5: 能否添加更多业务类型？

可以！步骤如下：

1. 在 `BusinessType` 枚举中添加新类型：
```java
public enum BusinessType {
    // 现有类型...
    YOUR_NEW_TYPE  // 添加新类型
}
```

2. 在配置文件中指定路由：
```yaml
model-router:
  strategy: BUSINESS_TYPE
  business-type:
    aliyun-types:
      - YOUR_NEW_TYPE
```

3. 在 `detectBusinessType()` 方法中添加检测逻辑：
```java
if (isYourNewType(userInput)) {
    return BusinessType.YOUR_NEW_TYPE;
}
```

---

## 附录

### 完整配置示例

```yaml
# Ollama配置
ollama:
  base-url: http://localhost:11434
  chat-model: qwen2.5:7b
  embedding-model: qwen3-embedding:0.6b
  timeout: 120s

# 阿里云DashScope配置
dashscope:
  api-key: ${DASHSCOPE_API_KEY:your-api-key-here}
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus
  timeout: 60s

# 模型路由配置
model-router:
  # 路由策略：PERCENTAGE（百分比）或 BUSINESS_TYPE（业务类型）
  strategy: PERCENTAGE

  # 百分比配置（当strategy=PERCENTAGE时生效）
  percentage:
    aliyun: 30  # 阿里云模型百分比（0-100）
    local: 70   # 本地模型百分比（0-100）

  # 业务类型路由（当strategy=BUSINESS_TYPE时生效）
  business-type:
    # 使用阿里云模型的业务类型
    aliyun-types:
      - COMPLEX_QUERY      # 复杂查询
      - LONG_CONTEXT       # 长上下文
      - HIGH_PRECISION     # 高精度要求
    # 使用本地模型的业务类型
    local-types:
      - SIMPLE_QA          # 简单问答
      - TOOL_CALLING       # 工具调用
      - GENERAL_CHAT       # 通用对话

# 日志配置
logging:
  level:
    root: INFO
    com.mark.knowledge: DEBUG
    com.mark.knowledge.chat.service.ModelRouterService: DEBUG
```

### 相关文档

- [LangChain4j文档](https://docs.langchain4j.dev/)
- [阿里云DashScope文档](https://help.aliyun.com/zh/dashscope/)
- [OpenAI API兼容性](https://help.aliyun.com/zh/dashscope/developer-reference/compatibility-of-openai-with-dashscope)

---

**最后更新**：2025-02-20
**文档版本**：1.0
