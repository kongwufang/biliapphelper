#!/usr/bin/env bash
# BiliDebug App 构建脚本（在 WSL 中运行）
# 用法: wsl -d Ubuntu-24.04 bash /mnt/c/Users/Admin/CodeBuddy/bililist/bili-debug-app/build_apk.sh
set -e
export ANDROID_HOME=/home/houlangs/android-sdk
export ANDROID_SDK_ROOT=/home/houlangs/android-sdk

SRC="$(cd "$(dirname "$0")" && pwd)"
DST=/home/houlangs/bili-debug-app
GRADLE=/home/houlangs/.gradle/wrapper/dists/gradle-9.6.1-bin/4ticwg1pgcbps2hj28r8so764/gradle-9.6.1/bin/gradle

echo "复制源码到 WSL 构建目录..."
rm -rf "$DST"
mkdir -p "$DST"
# 用 tar 排除 .gradle / build 等缓存（Windows 侧 lock 文件会读失败）
( cd "$SRC" && tar \
    --exclude=./.gradle \
    --exclude=./build \
    --exclude=./app/build \
    --exclude='*.apk' \
    --exclude=./local.properties \
    -cf - . ) | ( cd "$DST" && tar -xf - )

# 设备指纹 asset（device_meta/dt 硬指纹，位于项目外）
mkdir -p "$DST/app/src/main/assets"
if [ -f "$SRC/../_device_fp.json" ]; then
  cp "$SRC/../_device_fp.json" "$DST/app/src/main/assets/device_fp.json"
else
  echo "警告: 未找到 _device_fp.json，device_meta/dt 将为空"
fi

cd "$DST"
echo "开始构建 assembleRelease..."
"$GRADLE" assembleRelease --no-daemon --console=plain

echo ""
echo "构建完成，APK 输出:"
ls -la "$DST/app/build/outputs/apk/release/app-release.apk"

# 回拷 APK 到项目目录
cp "$DST/app/build/outputs/apk/release/app-release.apk" "$SRC/app-release.apk"
echo "已回拷到: $SRC/app-release.apk"
