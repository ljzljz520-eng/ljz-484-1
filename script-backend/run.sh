#!/usr/bin/env bash
# 编译并启动剧本创作管理服务（同时托管前端页面）
# 用法: ./run.sh [端口]    例如: ./run.sh 9000
set -e
cd "$(dirname "$0")"

mkdir -p out
echo "正在编译 Java 源码 ..."
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
rm -f sources.txt

echo "启动服务 ..."
java -cp out com.script.Main "$@"
