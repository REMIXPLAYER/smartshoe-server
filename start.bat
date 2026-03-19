@echo off
chcp 65001 >nul

REM SmartShoe Server 一键启动脚本
REM 支持 Windows

echo ========================================
echo   SmartShoe Server 启动脚本
echo ========================================

REM 检查 Java 版本
echo [1/4] 检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误：未找到 Java，请先安装 Java 17
    exit /b 1
)

for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
echo ✅ Java 版本: %JAVA_VERSION%

REM 检查配置文件
echo [2/4] 检查配置文件...
if not exist "src\main\resources\application-local.properties" (
    echo ⚠️  未找到 application-local.properties，使用示例配置创建...
    copy src\main\resources\application-local.properties.example src\main\resources\application-local.properties
    echo ⚠️  请编辑 src\main\resources\application-local.properties 填入实际的数据库密码
)

REM 构建项目
echo [3/4] 构建项目...
if exist "mvnw.cmd" (
    call mvnw.cmd clean package -DskipTests
) else (
    echo ❌ 错误：未找到 mvnw.cmd，请先运行 'mvn wrapper:wrapper'
    exit /b 1
)

if errorlevel 1 (
    echo ❌ 构建失败
    exit /b 1
)

REM 启动服务
echo [4/4] 启动 SmartShoe Server...
echo ========================================
java -jar target\smartshoe-server-0.0.1-SNAPSHOT.jar
