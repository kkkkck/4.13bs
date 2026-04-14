#!/bin/bash

# ================================================
# 后端 Spring Boot 应用启动脚本
# ================================================

# 配置参数
APP_NAME="question-bank-backend"
APP_JAR="shuati-website-0.0.1-SNAPSHOT-exec.jar"
APP_DIR="/opt/question-bank/backend"
LOG_FILE="/var/log/question-bank/backend.log"
PID_FILE="/var/run/question-bank.pid"

# JVM 参数配置
JAVA_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/./urandom"

# 创建日志目录
mkdir -p /var/log/question-bank

cd $APP_DIR

echo "================================================"
echo "          启动 $APP_NAME 应用"
echo "================================================"

# 检查是否已运行
if [ -f $PID_FILE ]; then
    PID=$(cat $PID_FILE)
    if kill -0 $PID 2>/dev/null; then
        echo "应用已在运行中，PID: $PID"
        exit 1
    else
        rm -f $PID_FILE
    fi
fi

# 启动应用
nohup java $JAVA_OPTS -jar $APP_JAR > $LOG_FILE 2>&1 &

# 获取PID
PID=$!
echo $PID > $PID_FILE

echo "应用启动成功，PID: $PID"
echo "日志文件: $LOG_FILE"

# 等待应用启动
sleep 5

# 检查启动状态
if kill -0 $PID 2>/dev/null; then
    echo "$APP_NAME 启动成功！"
else
    echo "$APP_NAME 启动失败，请查看日志: $LOG_FILE"
    rm -f $PID_FILE
    exit 1
fi
