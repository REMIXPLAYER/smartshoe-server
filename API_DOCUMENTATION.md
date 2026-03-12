# SmartShoe Server API 文档

## 基础信息

- **Base URL**: `http://localhost:8080`
- **数据库**: MySQL 8.0
- **数据格式**: JSON

---

## 一、认证接口

### 1. 用户登录

```http
POST /api/auth/login
Content-Type: application/json

{
    "email": "user@example.com",
    "password": "password123"
}
```

**响应示例**:
```json
{
    "success": true,
    "message": "登录成功",
    "data": {
        "userId": "user_1234567890_abc123",
        "username": "张三",
        "email": "user@example.com",
        "token": "user_1234567890_abc123_1234567890123_xyz789"
    }
}
```

### 2. 用户注册

```http
POST /api/auth/register
Content-Type: application/json

{
    "username": "张三",
    "email": "user@example.com",
    "password": "password123"
}
```

**响应示例**:
```json
{
    "success": true,
    "message": "注册成功",
    "data": {
        "userId": "user_1234567890_abc123",
        "username": "张三",
        "email": "user@example.com",
        "token": "user_1234567890_abc123_1234567890123_xyz789"
    }
}
```

### 3. 修改用户资料

```http
POST /api/auth/update-profile
Content-Type: application/json
Authorization: Bearer {token}

{
    "currentPassword": "password123",
    "newUsername": "李四",
    "newEmail": "newemail@example.com",
    "newPassword": "newpassword123"
}
```

### 4. 验证Token

```http
POST /api/auth/verify
Content-Type: application/json

{
    "token": "user_1234567890_abc123_1234567890123_xyz789"
}
```

### 5. 健康检查

```http
GET /api/auth/health
```

**响应示例**:
```json
{
    "status": "UP",
    "timestamp": "2024-01-15T10:30:00",
    "service": "smartshoe-auth"
}
```

---

## 二、传感器数据接口

### 1. 上传传感器数据

```http
POST /api/sensor/upload
Content-Type: application/json
Authorization: Bearer {token}

{
    "startTime": 1234567890123,
    "interval": 100,
    "data": [
        [100, 200, 300],
        [110, 210, 310],
        [120, 220, 320]
    ]
}
```

**响应示例**:
```json
{
    "success": true,
    "message": "上传成功",
    "data": {
        "recordId": "record_1234567890123_abc123",
        "dataCount": 3,
        "originalSize": 1024,
        "compressedSize": 256,
        "compressionRatio": 0.25
    }
}
```

### 2. 获取用户数据记录列表

```http
GET /api/sensor/records?page=0&size=20
Authorization: Bearer {token}
```

**响应示例**:
```json
{
    "success": true,
    "data": [
        {
            "recordId": "record_1234567890123_abc123",
            "startTime": 1234567890123,
            "endTime": 1234567890423,
            "dataCount": 3,
            "interval": 100,
            "originalSize": 1024,
            "compressedSize": 256,
            "createdAt": "2024-01-15T10:30:00"
        }
    ],
    "total": 100,
    "page": 0,
    "size": 20,
    "totalPages": 5
}
```

### 3. 获取单条记录详情

```http
GET /api/sensor/record/{recordId}
Authorization: Bearer {token}
```

**响应示例**:
```json
{
    "success": true,
    "message": "获取成功",
    "record": {
        "recordId": "record_1234567890123_abc123",
        "startTime": 1234567890123,
        "endTime": 1234567890423,
        "dataCount": 3,
        "interval": 100
    },
    "data": [
        [100, 200, 300],
        [110, 210, 310],
        [120, 220, 320]
    ]
}
```

### 4. 删除记录

```http
DELETE /api/sensor/record/{recordId}
Authorization: Bearer {token}
```

**响应示例**:
```json
{
    "success": true,
    "message": "删除成功"
}
```

---

## 三、管理员接口

### 1. 获取统计信息

```http
GET /admin/api/stats
```

**响应示例**:
```json
{
    "totalUsers": 100,
    "activeUsers": 85,
    "inactiveUsers": 15,
    "totalRecords": 500
}
```

### 2. 获取所有用户列表

```http
GET /admin/api/users
```

**响应示例**:
```json
[
    {
        "id": 1,
        "userId": "user_1234567890_abc123",
        "username": "张三",
        "email": "user@example.com",
        "status": "ACTIVE",
        "createdAt": "2024-01-15 10:30:00",
        "updatedAt": "2024-01-15 10:30:00",
        "lastLoginAt": "2024-01-15 12:00:00"
    }
]
```

### 3. 获取用户详情

```http
GET /admin/api/users/{userId}
```

### 4. 更新用户信息

```http
POST /admin/api/users/{userId}/update
Content-Type: application/x-www-form-urlencoded

username=新用户名&email=new@example.com&password=newpassword123
```

### 5. 更新用户状态

```http
POST /admin/api/users/{userId}/status?status=ACTIVE
```

### 6. 重置用户密码

```http
POST /admin/api/users/{userId}/reset-password?newPassword=newpassword123
```

### 7. 删除用户

```http
POST /admin/api/users/{userId}/delete
```

**说明**: 删除用户时会同时删除该用户的所有数据记录

### 8. 获取用户的数据记录

```http
GET /admin/api/users/{userId}/records
```

### 9. 删除数据记录

```http
POST /admin/api/records/{recordId}/delete
```

---

## 四、Web管理页面

### 页面列表

| 页面 | URL | 说明 |
|------|-----|------|
| 管理首页 | `/admin` | 统计概览、最近注册用户 |
| 用户列表 | `/admin/users` | 所有用户信息、编辑、删除 |
| 用户详情 | `/admin/users/{userId}` | 用户详细信息及数据记录 |
| 数据记录 | `/admin/records` | 所有传感器数据记录 |

---

## 五、数据模型

### User (用户)

```java
{
    "id": Long,              // 数据库ID
    "userId": String,        // 用户唯一标识
    "username": String,      // 用户名
    "email": String,         // 邮箱
    "password": String,      // 密码（加密存储）
    "status": String,        // ACTIVE/INACTIVE
    "createdAt": DateTime,   // 创建时间
    "updatedAt": DateTime,   // 更新时间
    "lastLoginAt": DateTime  // 最后登录时间
}
```

### SensorDataRecord (传感器数据记录)

```java
{
    "id": Long,              // 数据库ID
    "userId": String,        // 所属用户ID
    "recordId": String,      // 记录唯一标识
    "startTime": Long,       // 开始时间戳
    "endTime": Long,         // 结束时间戳
    "dataCount": Integer,    // 数据点数量
    "interval": Integer,     // 采样间隔(ms)
    "compressedData": String,// 压缩后的数据(Base64)
    "originalSize": Integer, // 原始数据大小(字节)
    "compressedSize": Integer,// 压缩后大小(字节)
    "createdAt": DateTime    // 创建时间
}
```

---

## 六、MySQL 8.0 配置

### 数据库连接配置 (application.properties)

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/smartshoe_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=your_password

spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=update
```

### 云服务器部署建议

1. **创建数据库**:
```sql
CREATE DATABASE smartshoe_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **创建专用用户**:
```sql
CREATE USER 'smartshoe_user'@'%' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON smartshoe_db.* TO 'smartshoe_user'@'%';
FLUSH PRIVILEGES;
```

3. **修改application.properties**:
```properties
spring.datasource.url=jdbc:mysql://your_server_ip:3306/smartshoe_db?useSSL=true&serverTimezone=Asia/Shanghai
spring.datasource.username=smartshoe_user
spring.datasource.password=your_strong_password
```

---

## 七、错误码

| HTTP状态码 | 含义 | 说明 |
|-----------|------|------|
| 200 | OK | 请求成功 |
| 400 | Bad Request | 请求参数错误 |
| 401 | Unauthorized | 未授权，Token无效 |
| 404 | Not Found | 资源不存在 |
| 500 | Internal Server Error | 服务器内部错误 |

---

## 八、数据隔离说明

- 每个用户的数据记录通过 `userId` 字段关联
- 用户只能访问自己的数据记录（通过Token验证）
- 管理员可以查看和管理所有用户及数据记录
- 删除用户时会级联删除该用户的所有数据记录
