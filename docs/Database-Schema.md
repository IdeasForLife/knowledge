# 数据库建表 SQL

本文档包含系统所需的数据库表结构。

## 📋 概述

系统使用 **SQLite** 作为关系型数据库，配合 **Qdrant** 向量数据库使用。

### 自动建表

默认情况下，Spring Boot + JPA 会在首次启动时自动创建表（配置：`spring.jpa.hibernate.ddl-auto: update`）

如需手动创建表，可参考以下 SQL。

---

## 🗄️ 表结构

### 1. users 表

用户表，存储系统用户信息。

```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE UNIQUE INDEX idx_users_username ON users(username);

-- 插入默认用户（密码：mark，使用 BCrypt 加密）
INSERT INTO users (username, password)
VALUES ('mark', '$2a$10$2B2tppkLZ4.dvCegcZ4l0.vDUU.atdOUryF//K2nZw1qTCXj8KHJK');
```

**字段说明**：
- `id` - 主键，自增
- `username` - 用户名，唯一
- `password` - 密码（BCrypt 加密）
- `created_at` - 创建时间

---

### 2. chat_messages 表

聊天消息表，存储用户和 AI 的对话记录。

```sql
CREATE TABLE chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id VARCHAR(255) NOT NULL,
    user_id INTEGER,
    role VARCHAR(20) NOT NULL,
    content VARCHAR(10000) NOT NULL,
    sources VARCHAR(5000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 创建索引
CREATE INDEX idx_chat_messages_conversation_id ON chat_messages(conversation_id);
CREATE INDEX idx_chat_messages_user_id ON chat_messages(user_id);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at);
```

**字段说明**：
- `id` - 主键，自增
- `conversation_id` - 会话 ID（如 "chat-xxx" 或 "agent-xxx"）
- `user_id` - 用户 ID（外键关联 users 表）
- `role` - 角色（"user" 或 "assistant"）
- `content` - 消息内容
- `sources` - 来源信息（JSON 字符串）
- `created_at` - 创建时间

**会话 ID 前缀**：
- `chat-` - 智能问答会话
- `agent-` - 智能体会话

---

### 3. domain_documents 表

领域文档表，存储上传的领域知识文档。

```sql
CREATE TABLE domain_documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domain VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(255),
    content TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    vector_count INTEGER,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME
);

-- 创建索引
CREATE INDEX idx_domain_documents_domain ON domain_documents(domain);
CREATE INDEX idx_domain_documents_status ON domain_documents(status);
CREATE INDEX idx_domain_documents_created_at ON domain_documents(created_at);
```

**字段说明**：
- `id` - 主键，自增
- `domain` - 领域名称（如 "TECHNOLOGY", "FINANCE" 等）
- `title` - 文档标题
- `source` - 来源
- `content` - 文档内容（TEXT 类型，支持长文本）
- `status` - 处理状态（"pending", "success", "failed"）
- `error_message` - 错误信息
- `vector_count` - 向量数量
- `created_at` - 创建时间
- `updated_at` - 更新时间
- `completed_at` - 完成时间

**支持的业务领域**：
- TECHNOLOGY - 技术文档
- FINANCE - 金融文档
- LAW - 法律文档
- MEDICINE - 医疗文档
- EDUCATION - 教育文档
- GOVERNMENT - 政府文档
- INSURANCE - 保险文档
- TAX - 税务文档
- HR - 人力资源
- COMPLIANCE - 合规文档
- 其他自定义领域

---

## 🔧 使用说明

### 方式一：自动建表（推荐）

无需手动创建表，启动应用即可：

```bash
mvn spring-boot:run
```

JPA 会自动检测实体类并创建表结构。

### 方式二：手动建表

如果需要手动创建表（如生产环境）：

```bash
# 1. 创建数据库文件
touch knowledge.db

# 2. 执行建表 SQL
sqlite3 knowledge.db < schema.sql

# 3. 启动应用
mvn spring-boot:run
```

### 方式三：禁用自动建表

修改 `application.yaml`：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none  # 禁用自动建表
    show-sql: true   # 显示 SQL 语句
```

---

## 📊 数据完整性

### 外键约束

- `chat_messages.user_id` → `users.id`
  - 删除用户时，相关消息不会被自动删除（需手动处理）

### 索引策略

- `username` - 唯一索引，加速用户名查询
- `conversation_id` - 普通索引，加速会话查询
- `user_id` - 普通索引，加速用户消息查询
- `created_at` - 普通索引，支持时间范围查询
- `domain` - 普通索引，加速领域文档查询
- `status` - 普通索引，加速状态筛选

---

## 🗑️ 清空数据

### 清空所有消息

```sql
DELETE FROM chat_messages;
```

### 清空所有用户（慎用）

```sql
DELETE FROM users WHERE username != 'mark';
```

### 重置数据库

```bash
# 删除数据库文件
rm knowledge.db

# 重启应用自动创建
mvn spring-boot:run
```

---

## 🔍 数据查询示例

### 查询用户的所有会话

```sql
SELECT DISTINCT conversation_id
FROM chat_messages
WHERE user_id = 1
  AND conversation_id LIKE 'agent-%'
ORDER BY created_at DESC;
```

### 查询会话历史

```sql
SELECT id, role, content, created_at
FROM chat_messages
WHERE conversation_id = 'agent-xxx'
ORDER BY created_at ASC;
```

### 查询待处理的文档

```sql
SELECT id, domain, title, status
FROM domain_documents
WHERE status = 'pending'
ORDER BY created_at ASC;
```

---

## 📝 注意事项

1. **SQLite 限制**
   - 单个数据库文件大小限制：~281 TB（理论值）
   - 单个 TEXT 字段最大：1 GB
   - 并发写入支持有限（建议单应用实例）

2. **密码加密**
   - 使用 BCrypt 加密算法
   - 每次加密结果不同（加盐）
   - 默认密码：`mark`

3. **数据备份**
   ```bash
   # 备份数据库
   cp knowledge.db knowledge.db.backup

   # 恢复数据库
   cp knowledge.db.backup knowledge.db
   ```

---

## 🚀 性能优化建议

### 定期清理历史消息

```sql
-- 删除 30 天前的消息
DELETE FROM chat_messages
WHERE created_at < datetime('now', '-30 days');
```

### 定期清理已完成的文档记录

```sql
-- 删除 90 天前已完成的文档
DELETE FROM domain_documents
WHERE status = 'success'
  AND completed_at < datetime('now', '-90 days');
```

### VACUUM 优化数据库

```bash
# 在 SQLite 中执行 VACUUM 命令回收空间
sqlite3 knowledge.db "VACUUM;"
```

---

## 📚 相关文档

- [README.md](../README.md) - 系统概述
- [配置说明](../README.md#-配置说明) - 数据库配置
