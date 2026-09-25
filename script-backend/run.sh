#!/usr/bin/env bash
# 编译并启动剧本管理后端（需要 JDK 11+）
set -e
cd "$(dirname "$0")"
mkdir -p out
find src -name "*.java" > out/sources.txt
javac -encoding UTF-8 -d out @out/sources.txt
java -cp out com.scriptmanager.Main
