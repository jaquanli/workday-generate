@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ##############################
REM 工作日Excel工具 — Windows 打包脚本
REM 产物: dist\工作日Excel工具-1.0.0.exe
REM
REM 前置: 需安装 JDK17+, Maven (mvn)
REM 可选: WiX Toolset (用于 .msi, https://wixtoolset.org)
REM ##############################

set APP_NAME=ge-workday
set APP_VERSION=1.0.0
set JAVAFX_VERSION=21.0.2
set JMODS_DIR=build\javafx-jmods-%JAVAFX_VERSION%
set JMODS_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VERSION%/openjfx-%JAVAFX_VERSION%_windows-x64_bin-jmods.zip

echo ===== 1. 下载 JavaFX jmods (windows-x64) =====
if exist "%JMODS_DIR%" (
    echo 已存在: %JMODS_DIR% (跳过下载)
) else (
    mkdir build 2>nul
    echo 下载中: %JMODS_URL%
    curl -# -L --max-time 300 -o build\javafx-jmods.zip %JMODS_URL%
    if %errorlevel% neq 0 (
        echo 下载失败! 请检查网络
        exit /b 1
    )
    tar -xf build\javafx-jmods.zip -C build\
    del build\javafx-jmods.zip

    REM 解压后目录可能是 javafx-jmods-21.0.2 或 openjfx-21.0.2
    if exist "build\javafx-jmods-%JAVAFX_VERSION%" (
        echo jmods 就位: %JMODS_DIR%
    ) else if exist "build\openjfx-%JAVAFX_VERSION%" (
        move "build\openjfx-%JAVAFX_VERSION%" "%JMODS_DIR%"
        echo jmods 就位(重命名): %JMODS_DIR%
    ) else (
        echo 解压后未找到预期 jmods 目录, 内容如下:
        dir build\
        exit /b 1
    )
)

echo.
echo ===== 2. Maven 构建 =====
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo Maven 构建失败!
    exit /b 1
)
if not exist "target\workday-excel-%APP_VERSION%.jar" (
    echo 构建失败: 找不到 target\workday-excel-%APP_VERSION%.jar
    exit /b 1
)

echo.
echo ===== 3. jpackage -^> exe =====
if exist dist rmdir /s /q dist
mkdir dist

REM jpackage 会复制 --input 目录内容到安装目录；去掉 --main-class，
REM 让 jpackage 从 jar 的 MANIFEST.MF 读取 Main-Class
REM (Spring Boot fat jar 的 Main-Class 是 JarLauncher，负责解嵌套加载)
jpackage ^
    --type exe ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --vendor "mycode" ^
    --description "工作日 Excel 生成导出工具" ^
    --input target ^
    --main-jar workday-excel-%APP_VERSION%.jar ^
    --icon src/main/resources/icon/ge-workday.ico ^
    --module-path "%JMODS_DIR%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,java.net.http ^
    --java-options "-Xmx256m" ^
    --dest dist

if exist "dist\%APP_NAME%-%APP_VERSION%.exe" (
    for %%A in ("dist\%APP_NAME%-%APP_VERSION%.exe") do set SIZE=%%~zA
    set /a SIZE_MB=!SIZE! / 1048576
    echo.
    echo 打包成功: dist\%APP_NAME%-%APP_VERSION%.exe (!SIZE_MB!MB)
    echo   用户双击 .exe 即可安装运行。
) else (
    echo 打包失败: 未生成预期文件
    exit /b 1
)

endlocal
