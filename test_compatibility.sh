#!/bin/bash

# MelodyClaw 兼容性测试脚本

APK_PATH="app/build/outputs/apk/release/app-release.apk"
PACKAGE_NAME="com.kimiclaw.pet"

echo "=== MelodyClaw 兼容性测试 ==="
echo ""

# 检查APK是否存在
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK文件不存在，请先构建应用"
    exit 1
fi

# 检查设备连接
echo "检查设备连接..."
DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)
if [ $DEVICES -eq 0 ]; then
    echo "❌ 未检测到设备，请连接设备或启动模拟器"
    exit 1
fi

echo "✅ 检测到 $DEVICES 台设备"
echo ""

# 获取设备信息
adb devices -l | grep "device$" | while read line; do
    SERIAL=$(echo $line | awk '{print $1}')
    echo "=== 设备: $SERIAL ==="

    # 获取设备信息
    BRAND=$(adb -s $SERIAL shell getprop ro.product.brand)
    MODEL=$(adb -s $SERIAL shell getprop ro.product.model)
    VERSION=$(adb -s $SERIAL shell getprop ro.build.version.release)
    SDK=$(adb -s $SERIAL shell getprop ro.build.version.sdk)

    echo "品牌: $BRAND"
    echo "型号: $MODEL"
    echo "Android版本: $VERSION (API $SDK)"
    echo ""

    # 卸载旧版本
    echo "卸载旧版本..."
    adb -s $SERIAL uninstall $PACKAGE_NAME 2>/dev/null

    # 安装新版本
    echo "安装应用..."
    adb -s $SERIAL install -r "$APK_PATH"

    if [ $? -eq 0 ]; then
        echo "✅ 安装成功"

        # 启动应用
        echo "启动应用..."
        adb -s $SERIAL shell am start -n $PACKAGE_NAME/.MainActivity

        # 等待5秒
        sleep 5

        # 检查应用是否运行
        RUNNING=$(adb -s $SERIAL shell "ps | grep $PACKAGE_NAME" | wc -l)
        if [ $RUNNING -gt 0 ]; then
            echo "✅ 应用正常运行"
        else
            echo "❌ 应用未运行，可能崩溃"
        fi

        # 获取日志
        echo "获取日志..."
        adb -s $SERIAL logcat -d | grep -i "melodyclaw\|kimiclaw" > "test_log_${SERIAL}_${SDK}.txt"
        echo "日志已保存到: test_log_${SERIAL}_${SDK}.txt"

    else
        echo "❌ 安装失败"
    fi

    echo ""
    echo "---"
    echo ""
done

echo "=== 测试完成 ==="
