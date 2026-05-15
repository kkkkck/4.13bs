# 本地运行零基础步骤

这份说明只讲“怎么把项目在自己电脑上跑起来”。项目分成四块：

1. MySQL：保存用户、题目、练习记录。
2. Redis：保存验证码、缓存、限流信息。
3. Ollama：提供本地 AI 答疑。
4. 前端和后端：真正的网站程序。

## 1. 先确认需要安装什么

你电脑上需要有：

- JDK 17：运行 Spring Boot 后端。
- Maven：执行 `mvn spring-boot:run`。
- Node.js 18 或更高：运行 Vue 前端。
- MySQL 8：保存数据库。
- Docker：你准备用它跑 Redis。
- Ollama：你准备用它跑 DeepSeek。

## 2. 准备 MySQL

后端默认连接：

```text
地址：127.0.0.1:3306
数据库：question_bank
用户名：root
密码：youngKCK20
```

如果你本机 MySQL 密码不是 `youngKCK20`，不要改代码，直接在启动后端前设置环境变量。

PowerShell 示例：

```powershell
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="你的MySQL密码"
```

第一次运行前，导入数据库表和默认数据：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 < deploy/init_database.sql
```

如果你没有本机 MySQL，也可以用 Docker 临时跑一个：

```powershell
docker run --name question-bank-mysql -p 3306:3306 `
  -e MYSQL_ROOT_PASSWORD=youngKCK20 `
  -e MYSQL_DATABASE=question_bank `
  -d mysql:8.0
```

等 MySQL 启动后再导入初始化 SQL。

## 3. 准备 Redis

最简单的本地 Redis 不设置密码：

```powershell
docker run --name question-bank-redis -p 6379:6379 -d redis:7-alpine
```

如果你用 `deploy/redis.conf` 运行，它里面默认有 `requirepass your_redis_password`，那后端也要设置：

```powershell
$env:REDIS_PASSWORD="your_redis_password"
```

## 4. 准备 Ollama + DeepSeek

启动 Ollama 后，确认模型名称。后端默认模型是：

```text
deepseek-r1:7b
```

常用命令：

```powershell
ollama pull deepseek-r1:7b
ollama run deepseek-r1:7b
```

如果你的模型名字不是 `deepseek-r1:7b`，启动后端前设置：

```powershell
$env:OLLAMA_MODEL="你的模型名"
```

如果暂时不想启用 AI 答疑，可以设置：

```powershell
$env:OLLAMA_ENABLED="false"
```

## 5. 启动后端

在项目根目录打开 PowerShell：

```powershell
cd backend
mvn spring-boot:run
```

看到类似 `Tomcat started on port(s): 8080` 就说明后端启动成功。

验证后端：

```powershell
curl http://127.0.0.1:8080/api/health
```

正常会返回：

```json
{"status":"UP"}
```

## 6. 启动前端

再开一个新的 PowerShell 窗口：

```powershell
cd frontend
npm install
npm run dev
```

打开浏览器访问：

```text
http://127.0.0.1:3000
```

前端开发服务器会把 `/api` 请求自动转发到后端 `http://localhost:8080`。

## 7. 默认管理员账号

初始化 SQL 会创建默认管理员：

```text
账号：admin@example.com
密码：admin123
```

登录后可以进入后台管理题目、分类和用户。

## 8. 常见问题

### 后端启动报 MySQL 连接失败

检查三件事：

1. MySQL 是否启动。
2. `question_bank` 数据库是否已创建。
3. `MYSQL_USERNAME`、`MYSQL_PASSWORD` 是否和你的本机 MySQL 一致。

### 后端启动报 Redis 连接失败

先确认 Redis 容器是否在运行：

```powershell
docker ps
```

如果 Redis 设置了密码，要同步设置 `REDIS_PASSWORD`。

### AI 答疑提示 Ollama 不可用

检查：

1. Ollama 是否启动。
2. `http://127.0.0.1:11434` 是否可访问。
3. `OLLAMA_MODEL` 是否等于 `ollama list` 里显示的模型名。

### 邮箱验证码发不出去

本地开发可以先关闭真实邮箱，打开调试验证码：

```powershell
$env:MAIL_ENABLED="false"
$env:MAIL_DEBUG_CODE_ENABLED="true"
```

这样接口会直接返回验证码，方便你本地注册和找回密码。
