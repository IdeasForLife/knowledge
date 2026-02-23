# ==============================
# 多阶段构建 - 构建阶段
# ==============================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 先复制 pom.xml，利用 Docker 缓存机制（依赖变更才重新下载）
COPY pom.xml .

# 下载依赖（可选：单独这一步可进一步优化缓存）
# RUN mvn dependency:go-offline -q

# 复制源码
COPY src ./src

# 构建应用（跳过测试以加快速度）
RUN mvn clean package -DskipTests -q


# ==============================
# 运行阶段 - 轻量级 JRE 环境
# ==============================
# 使用 Docker Hub 官方镜像
FROM eclipse-temurin:21-jre-alpine

# 设置时区为上海
ENV TZ=Asia/Shanghai \
    APP_DATA_DIR=/app/data

# 安装 tzdata（Alpine 需要显式安装时区数据）
RUN apk add --no-cache tzdata && \
    cp "/usr/share/zoneinfo/$TZ" /etc/localtime && \
    echo "$TZ" > /etc/timezone && \
    apk del tzdata

# 创建应用目录
WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 创建数据目录（用于 SQLite、上传文件等）
RUN mkdir -p "$APP_DATA_DIR"

# 暴露应用端口
EXPOSE 8080

# 健康检查（需确保应用有 /actuator/health 端点）
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=docker", \
    "-Duser.timezone=Asia/Shanghai", \
    "-jar", \
    "app.jar"]