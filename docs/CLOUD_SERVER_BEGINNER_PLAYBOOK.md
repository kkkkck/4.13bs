# 云服务器部署小白路线

这份文档先不要求你马上操作。等你买好服务器后，按这里一步步做，我可以继续带你执行。

## 1. 最推荐的上线方案

对这个项目来说，最省心的方案是一台云服务器跑全部服务：

```text
浏览器
  ↓
Nginx：负责网站入口和反向代理
  ↓
前端静态文件：Vue 打包后的 dist
  ↓
Spring Boot 后端：处理登录、题库、统计
  ↓
MySQL：保存核心数据
Redis：保存验证码、冷却限制、缓存
```

初期一台 2 核 4G 的轻量服务器通常够用。题库系统不是视频站，压力主要来自数据库和接口请求。

## 2. 购买服务器时怎么选

建议配置：

| 项 | 建议 |
| --- | --- |
| 系统 | Ubuntu 22.04 LTS |
| CPU/内存 | 2 核 4G 起步 |
| 硬盘 | 40G 以上 |
| 带宽 | 3M-5M 起步 |
| 地域 | 选离你和目标用户近的 |

不建议一开始就买很贵。先跑通，再看访问量升级。

## 3. 服务器需要开放哪些端口

安全组只开放这些：

```text
22    SSH 登录服务器
80    HTTP 网站访问
443   HTTPS 网站访问
```

不要把 MySQL 的 `3306` 和 Redis 的 `6379` 暴露到公网。它们只给服务器内部访问。

## 4. 第一次登录服务器

你会拿到一个公网 IP，例如 `1.2.3.4`。Windows 上可以用 PowerShell：

```powershell
ssh root@1.2.3.4
```

第一次登录后先更新系统：

```bash
apt update
apt upgrade -y
```

## 5. 安装基础环境

推荐用 Docker 部署，因为 Docker 能把 MySQL、Redis、后端、Nginx 都用配置文件管理。

```bash
apt install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sh
apt install -y docker-compose-plugin
docker --version
docker compose version
```

## 6. 拉取项目代码

```bash
cd /opt
git clone https://github.com/kkkkck/4.13bs.git shuati
cd /opt/shuati
```

## 7. 配置生产环境变量

上线时不能用开发默认密码。需要准备：

```text
MYSQL_ROOT_PASSWORD    MySQL root 密码
MYSQL_PASSWORD         项目数据库用户密码
REDIS_PASSWORD         Redis 密码
JWT_SECRET             JWT 签名密钥，越长越好
MAIL_USERNAME          发验证码的邮箱
MAIL_PASSWORD          邮箱 SMTP 授权码
```

可以先创建一个 `.env` 文件，Docker Compose 会读取。

## 8. 打包前后端

前端：

```bash
cd frontend
npm install
npm run build
```

后端：

```bash
cd ../backend
mvn clean package
```

## 9. 启动服务

```bash
cd /opt/shuati/deploy
docker compose up -d
docker compose ps
```

正常情况下你会看到 MySQL、Redis、backend、nginx 都是 running。

## 10. 配域名和 HTTPS

如果你买了域名：

1. 在域名控制台添加 A 记录，指向服务器公网 IP。
2. 服务器安装证书工具：

```bash
apt install -y certbot
```

3. 用 Nginx 配合证书，把 `http://域名` 升级成 `https://域名`。

这一步涉及具体域名和云厂商，等你买好服务器和域名后再实际操作。

## 11. 常用运维命令

查看服务：

```bash
docker compose ps
```

看后端日志：

```bash
docker logs -f question-bank-backend
```

重启后端：

```bash
docker compose restart backend
```

备份数据库：

```bash
docker exec question-bank-mysql mysqldump -uroot -p question_bank > backup.sql
```

## 12. 上线前检查清单

1. 后台管理员密码已改。
2. MySQL 和 Redis 没有暴露公网端口。
3. `JWT_SECRET` 不是默认值。
4. 邮箱 SMTP 能正常发验证码。
5. 前端能登录、刷题、查看个人中心。
6. 数据库有备份方案。

## 13. 你需要记住的核心原则

服务器上线不是一次性操作，而是“部署、验证、备份、监控”的组合。

对毕业答辩来说，你可以说：项目支持 Docker Compose 部署，前端由 Nginx 托管，后端以 Spring Boot 服务运行，MySQL 和 Redis 作为基础中间件在同一台服务器内网通信，公网只开放 HTTP/HTTPS。
