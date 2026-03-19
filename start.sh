#!/bin/bash

# SmartShoe Server 一键启动脚本
# 支持 Linux/Mac

set -e

echo "========================================"
echo "  SmartShoe Server 启动脚本"
echo "========================================"

# 检查 Java 版本
echo "[1/4] 检查 Java 环境..."
if ! command -v java &> /dev/null; then
    echo "❌ 错误：未找到 Java，请先安装 Java 17"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "✅ Java 版本: $JAVA_VERSION"

# 检查配置文件
echo "[2/4] 检查配置文件..."
if [ ! -f "src/main/resources/application-local.properties" ]; then
    echo "⚠️  未找到 application-local.properties，使用示例配置创建..."
    cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
    echo "⚠️  请编辑 src/main/resources/application-local.properties 填入实际的数据库密码"
fi

# 构建项目
echo "[3/4] 构建项目..."
if [ -f "mvnw" ]; then
    ./mvnw clean package -DskipTests
else
    echo "❌ 错误：未找到 mvnw，请先运行 'mvn wrapper:wrapper'"
    exit 1
fi

# 启动服务
echo "[4/4] 启动 SmartShoe Server..."
echo "========================================"
java -jar target/smartshoe-server-0.0.1-SNAPSHOT.jar
