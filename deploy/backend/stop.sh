#!/bin/bash

# ================================================
# 后端 Spring Boot 应用停止脚本
# ================================================

# 配置参数
APP_NAME="question-bank-backend"
PID_FILE="/var/run/question-bank.pid"

echo "================================================"
echo "          停止 $APP_NAME 应用"
echo "================================================"

# 检查PID文件是否存在
if [ ! -f $PID_FILE ]; then
    echo "应用未运行（PID文件不存在）"
    exit 0
fi

PID=$(cat $PID_FILE)

# 检查进程是否存在
if ! kill -0 $PID 2>/dev/null; then
    echo "应用未运行（进程不存在）"
    rm -f $PID_FILE
    exit 0
fi

# 优雅停止（发送SIGTERM信号）
echo "正在停止应用，PID: $PID"
kill -15 $PID

# 等待进程结束
count=0
max_wait=60
while kill -0 $PID 2>/dev/null; do
    sleep 1
    count=$((count + 1))
    if [ $count -ge $max_wait ]; then
        echo "等待超时，强制终止进程"
        kill -9 $PID
        break
    fi
    echo "等待进程结束... ($count/$max_wait)"
done

# 删除PID文件
rm -f $PID_FILE

echo "$APP_NAME 停止成功！"