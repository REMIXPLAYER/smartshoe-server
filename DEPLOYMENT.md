# SmartShoe Server 生产环境部署指南

## 一、打包项目

### 1. 清理并打包
```bash
mvn clean package -DskipTests
```

### 2. 生成的JAR文件位置
```
target/smartshoe-server-0.0.1-SNAPSHOT.jar
```

## 二、服务器环境准备

### 1. 安装Java 17
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# CentOS/RHEL
sudo yum install java-17-openjdk-devel

# 验证安装
java -version
```

### 2. 安装MySQL 8.0
```bash
# Ubuntu/Debian
sudo apt install mysql-server-8.0

# CentOS/RHEL
sudo yum install mysql-server

# 启动MySQL
sudo systemctl start mysql
sudo systemctl enable mysql
```

### 3. 配置MySQL
```bash
# 登录MySQL
sudo mysql -u root -p

# 创建数据库
CREATE DATABASE smartshoe_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 创建专用用户（推荐）
CREATE USER 'smartshoe'@'localhost' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON smartshoe_db.* TO 'smartshoe'@'localhost';
FLUSH PRIVILEGES;

# 退出
EXIT;
```

## 三、部署JAR包

### 1. 上传JAR文件到服务器
```bash
# 使用scp命令上传
scp target/smartshoe-server-0.0.1-SNAPSHOT.jar user@your-server-ip:/opt/smartshoe/
```

### 2. 创建应用目录
```bash
sudo mkdir -p /opt/smartshoe
sudo mkdir -p /opt/smartshoe/logs
cd /opt/smartshoe
```

### 3. 创建启动脚本
```bash
sudo tee /opt/smartshoe/start.sh << 'EOF'
#!/bin/bash
APP_NAME=smartshoe-server
JAR_NAME=smartshoe-server-0.0.1-SNAPSHOT.jar
LOG_FILE=logs/smartshoe-server.log

# 检查是否已在运行
PID=$(ps -ef | grep $JAR_NAME | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "Application is already running, PID: $PID"
    exit 1
fi

# 启动应用
nohup java -jar $JAR_NAME > $LOG_FILE 2>&1 &

# 等待启动
sleep 5
PID=$(ps -ef | grep $JAR_NAME | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "Application started successfully, PID: $PID"
else
    echo "Application failed to start"
    exit 1
fi
EOF

sudo chmod +x /opt/smartshoe/start.sh
```

### 4. 创建停止脚本
```bash
sudo tee /opt/smartshoe/stop.sh << 'EOF'
#!/bin/bash
APP_NAME=smartshoe-server
JAR_NAME=smartshoe-server-0.0.1-SNAPSHOT.jar

PID=$(ps -ef | grep $JAR_NAME | grep -v grep | awk '{print $2}')
if [ -n "$PID" ]; then
    echo "Stopping application, PID: $PID"
    kill -15 $PID
    
    # 等待进程结束
    for i in {1..30}; do
        sleep 1
        PID=$(ps -ef | grep $JAR_NAME | grep -v grep | awk '{print $2}')
        if [ -z "$PID" ]; then
            echo "Application stopped successfully"
            exit 0
        fi
    done
    
    # 强制结束
    echo "Force killing application"
    kill -9 $PID
else
    echo "Application is not running"
fi
EOF

sudo chmod +x /opt/smartshoe/stop.sh
```

### 5. 创建systemd服务（推荐）
```bash
sudo tee /etc/systemd/system/smartshoe.service << 'EOF'
[Unit]
Description=SmartShoe Server
After=syslog.target network.target mysql.service

[Service]
User=root
WorkingDirectory=/opt/smartshoe
ExecStart=/usr/bin/java -jar /opt/smartshoe/smartshoe-server-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 重载systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start smartshoe

# 设置开机自启
sudo systemctl enable smartshoe

# 查看状态
sudo systemctl status smartshoe

# 查看日志
sudo journalctl -u smartshoe -f
```

## 四、配置修改

### 1. 修改数据库连接（如需要）
编辑 `application.properties` 中的数据库配置：
```properties
spring.datasource.url=jdbc:mysql://your_mysql_host:3306/smartshoe_db?useSSL=true&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 2. 修改服务器端口（如需要）
```properties
server.port=8080
```

## 五、Nginx反向代理（可选）

```bash
sudo tee /etc/nginx/sites-available/smartshoe << 'EOF'
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/smartshoe /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

## 六、防火墙配置

```bash
# 开放8080端口
sudo ufw allow 8080/tcp

# 或开放HTTP端口
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

## 七、访问地址

- **管理后台**: http://your-server-ip:8080/admin
- **API接口**: http://your-server-ip:8080/api/

## 八、常用命令

```bash
# 启动应用
sudo systemctl start smartshoe

# 停止应用
sudo systemctl stop smartshoe

# 重启应用
sudo systemctl restart smartshoe

# 查看状态
sudo systemctl status smartshoe

# 查看日志
sudo journalctl -u smartshoe -f

# 手动启动（调试用）
cd /opt/smartshoe && java -jar smartshoe-server-0.0.1-SNAPSHOT.jar
```
