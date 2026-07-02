@echo off
chcp 936 >nul
setlocal enabledelayedexpansion

REM ##############################
REM ge-workday — Windows 打包脚本
REM 产物: dist\ge-workday-1.0.0.msi (WiX 安装包, 支持企业批量部署)
REM
REM 前置: 需安装 JDK17+, Maven (mvn), WiX Toolset 3.x
REM       WiX 安装: https://wixtoolset.org/releases/v3.14/
REM ##############################

REM 将 WiX Toolset 路径加入 PATH (兼容 v3.14 默认安装位置)
for %%P in (
    "%ProgramFiles(x86)%\WiX Toolset v3.14\bin"
    "%ProgramFiles(x86)%\WiX Toolset 3.14\bin"
    "%ProgramFiles%\WiX Toolset v3.14\bin"
) do (
    if exist "%%~P\candle.exe" set "PATH=%PATH%;%%~P"
)

set APP_NAME=ge-workday
set APP_VERSION=1.0.0
set JAVAFX_VERSION=21.0.2
set JMODS_DIR=build\javafx-jmods-%JAVAFX_VERSION%
set JMODS_URL=https://download2.gluonhq.com/openjfx/%JAVAFX_VERSION%/openjfx-%JAVAFX_VERSION%_windows-x64_bin-jmods.zip

echo ===== 1. 下载 JavaFX jmods (windows-x64) =====
if exist "%JMODS_DIR%" (
    echo 已存在: %JMODS_DIR% ^(跳过下载^)
) else (
    mkdir build 2>nul
    echo 下载中: %JMODS_URL%
    curl -# -L --max-time 300 -o build\javafx-jmods.zip %JMODS_URL%
    if !errorlevel! neq 0 (
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
        echo jmods 就位^(重命名^): %JMODS_DIR%
    ) else (
        echo 解压后未找到预期 jmods 目录, 内容如下:
        dir build\
        exit /b 1
    )
)

echo.
echo ===== 2. Maven 构建 =====
call mvn clean package -DskipTests -q
if !errorlevel! neq 0 (
    echo Maven 构建失败!
    exit /b 1
)
if not exist "target\workday-generate-%APP_VERSION%.jar" (
    echo 构建失败: 找不到 target\workday-generate-%APP_VERSION%.jar
    exit /b 1
)

echo.
echo ===== 3. jpackage 打包 msi =====

REM 检测 WiX (candle/light) — jpackage --type msi 必需
where candle >nul 2>nul
if !errorlevel! neq 0 (
    echo 未找到 candle.exe, 请安装 WiX Toolset 3.x: https://wixtoolset.org/releases/v3.14/
    exit /b 1
)

if exist dist rmdir /s /q dist
mkdir dist

REM jpackage --type msi: 内部调用 WiX (candle+light) 生成 msi 安装包
REM 产物内嵌完整 JRE + JavaFX, 用户无需预装 Java; 支持组策略下发/静默部署
jpackage --type msi --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "mycode" --description "Workday Excel Generator" --input target --main-jar workday-generate-%APP_VERSION%.jar --icon src/main/resources/icon/ge-workday.ico --module-path "%JMODS_DIR%" --add-modules javafx.controls,javafx.fxml,javafx.graphics,java.net.http --java-options "-Xmx256m" --dest dist --win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid "B7E3F2A1-4C5D-6E8F-9A0B-1C2D3E4F5A6B"
if !errorlevel! neq 0 (
    echo jpackage msi 打包失败!
    exit /b 1
)

set JP_PRODUCT=dist\%APP_NAME%-%APP_VERSION%.msi
if exist "!JP_PRODUCT!" (
    for %%A in ("!JP_PRODUCT!") do set SIZE=%%~zA
    set /a SIZE_MB=!SIZE! / 1048576
    echo.
    echo 打包成功: !JP_PRODUCT! ^(!SIZE_MB!MB^)
    echo   用户双击 .msi 即可安装 (内含 JRE, 无需预装 Java)。
    echo   企业批量部署: msiexec /i %APP_NAME%-%APP_VERSION%.msi /qn INSTALLDIR=路径
) else (
    echo 打包失败: 未生成预期文件 !JP_PRODUCT!
    exit /b 1
)

endlocal
