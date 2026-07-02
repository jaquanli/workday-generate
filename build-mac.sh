#!/bin/bash
set -euo pipefail

##############################
# 工作日Excel工具 — macOS 打包脚本
# 产物: dist/工作日Excel工具-1.0.0.dmg
##############################

APP_NAME="ge-workday"
APP_VERSION="1.0.0"
JAVAFX_VERSION="21.0.2"

# ---------- 检测架构 ----------
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    JFX_ARCH="aarch64"
    DMG_ARCH="arm64"
elif [ "$ARCH" = "x86_64" ]; then
    JFX_ARCH="x64"
    DMG_ARCH="x64"
else
    echo "❌ 不支持的架构: $ARCH"
    exit 1
fi

JMODS_DIR="build/javafx-jmods-${JAVAFX_VERSION}"
JMODS_URL="https://download2.gluonhq.com/openjfx/${JAVAFX_VERSION}/openjfx-${JAVAFX_VERSION}_osx-${JFX_ARCH}_bin-jmods.zip"

echo "===== 1. 下载 JavaFX jmods (${JFX_ARCH}) ====="
if [ -d "$JMODS_DIR" ]; then
    echo "已存在: $JMODS_DIR (跳过下载)"
else
    mkdir -p build
    echo "下载中: $JMODS_URL"
    curl -# -L --max-time 300 -o build/javafx-jmods.zip "$JMODS_URL"
    unzip -q build/javafx-jmods.zip -d build/
    rm build/javafx-jmods.zip
    # gluon 解压后目录名可能为 javafx-jmods-21.0.2 或 openjfx-...
    if [ -d "build/javafx-jmods-${JAVAFX_VERSION}" ]; then
        echo "jmods 就位: $JMODS_DIR"
    elif [ -d "build/openjfx-${JAVAFX_VERSION}" ]; then
        mv "build/openjfx-${JAVAFX_VERSION}" "$JMODS_DIR"
        echo "jmods 就位(重命名): $JMODS_DIR"
    else
        echo "❌ 解压后未找到预期 jmods 目录, 内容如下:"
        ls -la build/
        exit 1
    fi
fi

echo ""
echo "===== 2. Maven 构建 (arch=${JFX_ARCH}) ====="
mvn clean package -DskipTests -q 2>&1 | tail -5

if [ ! -f "target/workday-excel-${APP_VERSION}.jar" ]; then
    echo "❌ 构建失败: 找不到 target/workday-excel-${APP_VERSION}.jar"
    exit 1
fi

echo ""
echo "===== 3. jpackage → DMG ====="
rm -rf dist
mkdir -p dist

# jpackage 会复制 --input 目录内容到 .app；这里只放 jar
# 去掉 --main-class，让 jpackage 从 jar 的 MANIFEST.MF 读取 Main-Class
# (Spring Boot fat jar 的 Main-Class 是 JarLauncher，它负责解嵌套加载)
jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "mycode" \
    --description "工作日 Excel 生成导出工具" \
    --input target \
    --main-jar "workday-excel-${APP_VERSION}.jar" \
    --icon src/main/resources/icon/ge-workday.icns \
    --module-path "$JMODS_DIR" \
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,java.net.http \
    --java-options "-Xmx256m" \
    --dest dist \
    --verbose 2>&1 | tail -3

DMG_FILE="dist/${APP_NAME}-${APP_VERSION}.dmg"
if [ -f "$DMG_FILE" ]; then
    SIZE=$(du -h "$DMG_FILE" | cut -f1)
    echo ""
    echo "✅ 打包成功: $DMG_FILE ($SIZE)"
    echo "   用户双击 .dmg 安装后即可在「启动台」运行。"
else
    echo "❌ 打包失败: 未生成预期文件 $DMG_FILE"
    exit 1
fi
