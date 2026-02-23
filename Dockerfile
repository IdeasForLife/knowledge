# 多阶段构建 - 构建阶段
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 复制 pom.xml 和 src (利用Docker缓存层)
COPY pom.xml .
COPY src ./src

# 构建应用 (跳过测试以加快构建速度)
RUN mvn clean package -DskipTests -q

# 运行阶段 - 使用轻量级JRE
FROM eclipse-temurin:21-jre

# 设置时区
ENV TZ=Asia/Shanghai

# 创建应用目录
WORKDIR /app

# 从构建阶段复制jar包
COPY --from=builder /app/target/*.jar app.jar

# 创建数据目录用于持久化SQLite数据库和上传文件
RUN mkdir -p /app/data

# 设置数据目录环境变量（可选，用于代码中引用）
ENV APP_DATA_DIR=/app/data

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 运行应用
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=docker", \
    "-Duser.timezone=GMT+08", \
    "-jar", \
    "app.jar"]

# 可选：覆盖默认命令，允许传递JVM参数
CMD ["java", "-jar", "app.jar"]
