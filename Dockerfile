# 多阶段构建，根据 Spring Boot 官方最佳实践
# 参考: https://spring.io/guides/gs/spring-boot-docker/

# 第一阶段：构建应用
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 复制 Maven Wrapper 和 pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 下载依赖（利用 Docker 缓存层）
RUN ./mvnw dependency:go-offline -B

# 复制源代码
COPY src src

# 构建应用
RUN ./mvnw clean package -DskipTests -B

# 第二阶段：运行应用
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 创建非 root 用户（安全最佳实践）
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
