#!/bin/bash

# ================================================
# 刷题网站服务器环境一键安装脚本
# 适用系统：CentOS 7 / Ubuntu 20.04
# ================================================

set -e

echo "================================================"
echo "          刷题网站服务器环境一键安装脚本"
echo "================================================"

# 检测操作系统
OS=$(cat /etc/os-release | grep -E "^ID=" | cut -d'=' -f2 | tr -d '"')
echo "检测到操作系统: $OS"

# 更新系统
echo "开始更新系统..."
if [ "$OS" == "centos" ]; then
    yum update -y
elif [ "$OS" == "ubuntu" ]; then
    apt update && apt upgrade -y
fi

# 安装基础依赖
echo "安装基础依赖..."
if [ "$OS" == "centos" ]; then
    yum install -y wget curl vim unzip tar
elif [ "$OS" == "ubuntu" ]; then
    apt install -y wget curl vim unzip tar
fi

# ================================================
# 安装 Java 17
# ================================================
echo "安装 Java 17..."
if [ "$OS" == "centos" ]; then
    yum install -y java-17-openjdk java-17-openjdk-devel
else
    apt install -y openjdk-17-jdk openjdk-17-jre
fi

java -version
echo "Java 17 安装完成"

# ================================================
# 安装 MySQL 8.0
# ================================================
echo "安装 MySQL 8.0..."
if [ "$OS" == "centos" ]; then
    # 添加 MySQL YUM 仓库
    wget https://dev.mysql.com/get/mysql80-community-release-el7-3.noarch.rpm
    rpm -ivh mysql80-community-release-el7-3.noarch.rpm
    yum install -y mysql-community-server
    
    # 启动 MySQL
    systemctl start mysqld
    systemctl enable mysqld
    
    # 获取初始密码
    INITIAL_PASSWORD=$(grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}')
    echo "MySQL 初始密码: $INITIAL_PASSWORD"
    echo "请记住此密码，后续需要使用"
    
else
    # Ubuntu 安装 MySQL 8.0
    apt install -y mysql-server
    
    # 启动 MySQL
    systemctl start mysql
    systemctl enable mysql
    
    # 获取初始密码（Ubuntu 默认无密码）
    echo "Ubuntu MySQL 默认无密码，请直接登录"
fi

echo "MySQL 8.0 安装完成"

# ================================================
# 安装 Redis 7.x
# ================================================
echo "安装 Redis 7.x..."
if [ "$OS" == "centos" ]; then
    # 添加 EPEL 仓库
    yum install -y epel-release
    yum install -y redis
else
    # Ubuntu 安装 Redis
    apt install -y redis-server
fi

# 启动 Redis
systemctl start redis
systemctl enable redis

echo "Redis 安装完成"

# ================================================
# 安装 Nginx
# ================================================
echo "安装 Nginx..."
if [ "$OS" == "centos" ]; then
    yum install -y nginx
else
    apt install -y nginx
fi

# 启动 Nginx
systemctl start nginx
systemctl enable nginx

echo "Nginx 安装完成"

echo "================================================"
echo "              环境安装完成！"
echo "================================================"
echo ""
echo "已安装的服务："
echo "  - Java 17"
echo "  - MySQL 8.0 (端口: 3306)"
echo "  - Redis 7.x (端口: 6379)"
echo "  - Nginx (端口: 80)"
echo ""
echo "后续步骤："
echo " 1. 配置 MySQL 密码和创建数据库"
echo " 2. 配置 Redis 密码"
echo " 3. 部署后端应用"
echo " 4. 部署前端静态文件"
echo " 5. 配置 Nginx"