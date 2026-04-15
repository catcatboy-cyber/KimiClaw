#!/bin/bash

# KimiClaw 构建脚本

echo "🦞 开始构建 KimiClaw 小龙虾..."

# 检查是否安装了Android SDK
if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    echo "❌ 错误: 未设置 ANDROID_SDK_ROOT 或 ANDROID_HOME 环境变量"
    echo "请设置Android SDK路径"
    exit 1
fi

# 使用gradlew构建
if [ -f "./gradlew" ]; then
    echo "📦 使用 Gradle Wrapper 构建..."
    ./gradlew assembleDebug
else
    echo "📦 使用系统 Gradle 构建..."
    gradle assembleDebug
fi

# 检查构建结果
if [ $? -eq 0 ]; then
    echo "✅ 构建成功！"
    echo "📱 APK位置: app/build/outputs/apk/debug/app-debug.apk"
    
    # 复制到输出目录
    mkdir -p output
    cp app/build/outputs/apk/debug/app-debug.apk output/KimiClaw-v1.0.apk
    echo "📦 已复制到: output/KimiClaw-v1.0.apk"
else
    echo "❌ 构建失败，请检查错误信息"
    exit 1
fi

echo "🦞 构建完成！"
