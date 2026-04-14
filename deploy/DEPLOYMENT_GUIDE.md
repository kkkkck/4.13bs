# 刷题网站服务器部署方案

## 一、环境要求

- **服务器配置**: 阿里云/腾讯云服务器（2核4G配置及以上）
- **操作系统**: CentOS 7 或 Ubuntu 20.04
- **所需服务**:
  - MySQL 8.0（端口 3306）
  - Redis 7.x（端口 6379）
  - Java 17（后端运行环境）
  - Nginx（端口 80，前端静态文件和反向代理）

---

## 二、部署方式

### 方式一：手动部署（推荐）

#### 2.1 环境安装

```bash
# 上传安装脚本到服务器
scp deploy/install_env.sh root@your_server_ip:/tmp/

# 登录服务器
ssh root@your_server_ip

# 执行安装脚本
chmod +x /tmp/install_env.sh
/tmp/install_env.sh
```

#### 2.2 MySQL 配置

```bash
# 登录 MySQL（CentOS）
mysql -uroot -p
# 输入初始密码（安装脚本输出的密码）

# 或者 Ubuntu（默认无密码）
mysql -uroot

# 设置新密码
ALTER USER 'root'@'localhost' IDENTIFIED BY 'your_mysql_root_password';

# 创建数据库用户
CREATE USER 'question_user'@'localhost' IDENTIFIED BY 'your_mysql_password';
GRANT ALL PRIVILEGES ON question_bank.* TO 'question_user'@'localhost';
FLUSH PRIVILEGES;

# 退出 MySQL
exit

# 执行初始化脚本
mysql -uroot -p your_mysql_root_password < /path/to/init_database.sql
```

#### 2.3 Redis 配置

```bash
# 备份原配置文件
cp /etc/redis/redis.conf /etc/redis/redis.conf.bak

# 上传配置文件
scp deploy/redis.conf root@your_server_ip:/etc/redis/redis.conf

# 修改配置文件中的密码（可选但推荐）
sed -i 's/your_redis_password/your_actual_redis_password/g' /etc/redis/redis.conf

# 重启 Redis
systemctl restart redis

# 验证 Redis
redis-cli -a your_redis_password ping
# 应返回 PONG
```

#### 2.4 后端应用部署

```bash
# 创建应用目录
mkdir -p /opt/question-bank/backend

# 上传后端 JAR 包和启动脚本
scp backend/question-bank-0.0.1-SNAPSHOT.jar root@your_server_ip:/opt/question-bank/backend/
scp deploy/backend/start.sh root@your_server_ip:/opt/question-bank/backend/
scp deploy/backend/stop.sh root@your_server_ip:/opt/question-bank/backend/

# 设置执行权限
chmod +x /opt/question-bank/backend/start.sh
chmod +x /opt/question-bank/backend/stop.sh

# 创建日志目录
mkdir -p /var/log/question-bank

# 启动后端应用
cd /opt/question-bank/backend
./start.sh

# 检查启动状态
curl http://localhost:8080/api/health
```

#### 2.5 前端部署

```bash
# 构建前端项目（本地执行）
cd frontend
npm run build

# 创建前端目录
ssh root@your_server_ip "mkdir -p /usr/share/nginx/html/question-bank"

# 上传前端静态文件
scp -r frontend/dist/* root@your_server_ip:/usr/share/nginx/html/question-bank/
```

#### 2.6 Nginx 配置

```bash
# 备份原配置文件
cp /etc/nginx/nginx.conf /etc/nginx/nginx.conf.bak

# 上传配置文件
scp deploy/nginx.conf root@your_server_ip:/etc/nginx/nginx.conf

# 检查配置语法
nginx -t

# 重启 Nginx
systemctl restart nginx
```

---

### 方式二：Docker Compose 部署

#### 2.1 安装 Docker 和 Docker Compose

```bash
# CentOS
yum install -y docker docker-compose
systemctl start docker
systemctl enable docker

# Ubuntu
apt install -y docker.io docker-compose
systemctl start docker
systemctl enable docker
```

#### 2.2 准备部署文件

```bash
# 创建目录结构
mkdir -p /opt/question-bank/deploy
cd /opt/question-bank/deploy

# 创建子目录
mkdir -p mysql/data redis/data nginx/html nginx/ssl logs

# 上传所有部署文件
scp -r deploy/* root@your_server_ip:/opt/question-bank/deploy/

# 上传后端 JAR 包
scp backend/question-bank-0.0.1-SNAPSHOT.jar root@your_server_ip:/opt/question-bank/deploy/backend/

# 构建前端并上传
cd frontend
npm run build
scp -r dist/* root@your_server_ip:/opt/question-bank/deploy/nginx/html/
```

#### 2.3 修改配置文件

编辑 `docker-compose.yml`，修改以下密码：
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `SPRING_REDIS_PASSWORD`（需与 redis.conf 中的密码一致）
- `JWT_SECRET`

编辑 `redis.conf`，修改密码：
- `requirepass your_redis_password`

#### 2.4 启动服务

```bash
cd /opt/question-bank/deploy
docker-compose up -d

# 查看日志
docker-compose logs -f

# 检查服务状态
docker-compose ps
```

---

## 三、配置说明

### 3.1 后端配置文件（application.yml）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/question_bank?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: question_user
    password: your_mysql_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  redis:
    host: localhost
    port: 6379
    password: your_redis_password
    timeout: 10000ms

jwt:
  secret: your_jwt_secret_key_here_must_be_at_least_256_bits
  expiration: 604800000  # 7天（毫秒）

logging:
  level:
    com.example.刷题: DEBUG
```

### 3.2 Nginx 配置说明

- 前端静态文件路径: `/usr/share/nginx/html/question-bank/`
- API 反向代理: `/api/*` -> `http://localhost:8080/api/*`
- 前端路由模式: History 模式（需配置 `try_files $uri $uri/ /index.html`）

---

## 四、服务管理

### 4.1 后端服务

```bash
# 启动
/opt/question-bank/backend/start.sh

# 停止
/opt/question-bank/backend/stop.sh

# 查看日志
tail -f /var/log/question-bank/backend.log
```

### 4.2 Nginx

```bash
# 启动
systemctl start nginx

# 停止
systemctl stop nginx

# 重启
systemctl restart nginx

# 查看状态
systemctl status nginx

# 检查配置
nginx -t
```

### 4.3 MySQL

```bash
# 启动
systemctl start mysqld

# 停止
systemctl stop mysqld

# 重启
systemctl restart mysqld

# 查看状态
systemctl status mysqld

# 登录
mysql -uquestion_user -p your_mysql_password
```

### 4.4 Redis

```bash
# 启动
systemctl start redis

# 停止
systemctl stop redis

# 重启
systemctl restart redis

# 查看状态
systemctl status redis

# 登录
redis-cli -a your_redis_password
```

---

## 五、安全建议

1. **防火墙配置**: 只开放必要端口（80, 443, 3306（仅内网）, 6379（仅内网））
2. **SSL 证书**: 配置 HTTPS（使用 Let's Encrypt）
3. **密码管理**: 使用强密码，定期更换
4. **备份策略**: 定期备份数据库和配置文件
5. **安全组**: 限制访问来源 IP

---

## 六、默认管理员账号

- **邮箱**: admin@example.com
- **密码**: admin123

---

## 七、文件清单

```
deploy/
├── install_env.sh          # 环境一键安装脚本
├── init_database.sql       # 数据库初始化脚本
├── redis.conf              # Redis 配置文件
├── nginx.conf              # Nginx 配置文件
├── docker-compose.yml      # Docker Compose 配置
├── backend/
│   ├── start.sh            # 后端启动脚本
│   └── stop.sh             # 后端停止脚本
└── DEPLOYMENT_GUIDE.md     # 部署文档（本文件）
```

---

## 八、故障排查

### 8.1 后端启动失败

```bash
# 查看日志
tail -f /var/log/question-bank/backend.log

# 常见问题：
# 1. 数据库连接失败 - 检查数据库配置和网络
# 2. Redis 连接失败 - 检查 Redis 配置和网络
# 3. 端口被占用 - 使用 netstat -tlnp | grep 8080 查看
```

### 8.2 Nginx 配置错误

```bash
# 检查配置语法
nginx -t

# 查看错误日志
tail -f /var/log/nginx/error.log
```

### 8.3 前端无法访问

```bash
# 检查文件权限
ls -la /usr/share/nginx/html/question-bank/

# 检查 Nginx 状态
systemctl status nginx

# 检查防火墙
firewall-cmd --list-all  # CentOS
ufw status              # Ubuntu
```