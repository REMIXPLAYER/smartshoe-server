# SmartShoe Server

SmartShoe 服务器端 - 基于 Spring Boot 的认证和数据管理服务。

## 🚀 一键式部署

### 方式一：使用启动脚本（推荐）

#### Windows
```bash
# 1. Clone 项目
git clone https://github.com/REMIXPLAYER/smartshoe-server.git
cd smartshoe-server

# 2. 运行一键启动脚本
start.bat
```

#### Linux/Mac
```bash
# 1. Clone 项目
git clone https://github.com/REMIXPLAYER/smartshoe-server.git
cd smartshoe-server

# 2. 赋予脚本执行权限
chmod +x start.sh

# 3. 运行一键启动脚本
./start.sh
```

脚本会自动：
1. ✅ 检查 Java 17 环境
2. ✅ 创建本地配置文件（如果不存在）
3. ✅ 使用 Maven Wrapper 构建项目（无需安装 Maven）
4. ✅ 启动服务

### 方式二：使用 Docker（最简单）

```bash
# 1. Clone 项目
git clone https://github.com/REMIXPLAYER/smartshoe-server.git
cd smartshoe-server

# 2. 使用 Docker Compose 一键启动（包含 MySQL）
docker-compose up -d

# 服务将在 http://localhost:8080 启动
```

### 方式三：手动部署

#### 环境要求
- Java 17+
- Maven 3.6+（可选，如果使用 Maven Wrapper 则不需要）
- MySQL 8.0

#### 步骤

```bash
# 1. Clone 项目
git clone https://github.com/REMIXPLAYER/smartshoe-server.git
cd smartshoe-server

# 2. 配置数据库
# 复制示例配置文件
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties

# 编辑配置文件，填入实际的数据库密码
# Windows: notepad src/main/resources/application-local.properties
# Linux/Mac: vim src/main/resources/application-local.properties

# 3. 构建项目
# 使用 Maven Wrapper（推荐，无需安装 Maven）
./mvnw clean package -DskipTests

# 或使用系统 Maven
mvn clean package -DskipTests

# 4. 运行
java -jar target/smartshoe-server-0.0.1-SNAPSHOT.jar
```

## ⚙️ 配置说明

### 数据库配置

编辑 `src/main/resources/application-local.properties`：

```properties
# MySQL 配置
spring.datasource.url=jdbc:mysql://localhost:3306/smartshoe_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=你的密码
```

### 环境变量配置（生产环境推荐）

```bash
export DB_URL=jdbc:mysql://localhost:3306/smartshoe_db
export DB_USERNAME=root
export DB_PASSWORD=你的密码
java -jar target/smartshoe-server-0.0.1-SNAPSHOT.jar
```

## 📁 项目结构

```
smartshoe-server/
├── src/
│   ├── main/
│   │   ├── java/           # Java 源代码
│   │   │   └── com/sensors/smartshoeserver/
│   │   │       ├── config/         # 配置类
│   │   │       ├── controller/     # REST API 控制器
│   │   │       ├── dto/            # 数据传输对象
│   │   │       ├── entity/         # JPA 实体
│   │   │       ├── repository/     # 数据访问层
│   │   │       ├── service/        # 业务逻辑层
│   │   │       └── util/           # 工具类
│   │   └── resources/
│   │       ├── application.properties           # 主配置
│   │       ├── application-local.properties.example  # 本地配置示例
│   │       ├── static/             # 静态资源
│   │       └── templates/          # Thymeleaf 模板
│   └── test/               # 测试代码
├── mvnw / mvnw.cmd         # Maven Wrapper
├── pom.xml                 # Maven 配置
├── Dockerfile              # Docker 构建文件
├── docker-compose.yml      # Docker Compose 配置
├── start.sh / start.bat    # 一键启动脚本
└── README.md               # 本文件
```

## 🔧 技术栈

- **框架**: Spring Boot 3.2.3
- **Java 版本**: Java 17
- **数据库**: MySQL 8.0
- **ORM**: Spring Data JPA
- **模板引擎**: Thymeleaf
- **安全**: JWT (JJWT 0.12.3)
- **构建工具**: Maven

## 📚 API 文档

启动服务后访问：
- REST API: http://localhost:8080/api/
- 管理后台: http://localhost:8080/admin
- API 文档: 查看 `API_DOCUMENTATION.md`

## 🛠️ 开发指南

### 运行测试
```bash
./mvnw test
```

### 开发模式运行
```bash
./mvnw spring-boot:run
```

### 打包部署
```bash
./mvnw clean package
```

## 📝 注意事项

1. **数据库密码**: 生产环境请使用环境变量或外部配置，不要提交到 Git
2. **JWT 密钥**: 生产环境请修改 `application.properties` 中的 JWT 密钥
3. **日志文件**: 日志默认输出到 `logs/` 目录，已配置自动轮转

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License
