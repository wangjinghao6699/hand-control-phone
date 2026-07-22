@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   手势控制抖音 - 一键编译安装脚本
echo ============================================
echo.

:: 检测 Java
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java，请先安装 JDK 17
    echo 下载地址: https://adoptium.net/download/
    pause
    exit /b 1
)

:: 设置 JDK 17（如果存在）
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot" (
    set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot
    echo [信息] 使用 JDK 17: %JAVA_HOME%
)
if exist "C:\Program Files\Java\jdk-17" (
    set JAVA_HOME=C:\Program Files\Java\jdk-17
    echo [信息] 使用 JDK 17: %JAVA_HOME%
)

:: 设置 Android SDK
if not defined ANDROID_HOME (
    set ANDROID_HOME=%USERPROFILE%\Android\Sdk
    set ANDROID_SDK_ROOT=%USERPROFILE%\Android\Sdk
)

echo [信息] ANDROID_HOME = %ANDROID_HOME%

:: 检查 Android SDK 是否存在
if not exist "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo.
    echo [信息] 未找到 Android SDK，正在下载...
    
    mkdir "%ANDROID_HOME%\cmdline-tools" 2>nul
    
    echo [信息] 从腾讯云镜像下载 SDK 命令行工具...
    powershell -Command "Invoke-WebRequest -Uri 'https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-11076708_latest.zip' -OutFile '%ANDROID_HOME%\cmdline-tools\tools.zip'" 2>nul
    
    if not exist "%ANDROID_HOME%\cmdline-tools\tools.zip" (
        echo [信息] 从 Google 直接下载...
        powershell -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile '%ANDROID_HOME%\cmdline-tools\tools.zip'"
    )
    
    if exist "%ANDROID_HOME%\cmdline-tools\tools.zip" (
        echo [信息] 解压 SDK 工具...
        powershell -Command "Expand-Archive -Path '%ANDROID_HOME%\cmdline-tools\tools.zip' -DestinationPath '%ANDROID_HOME%\cmdline-tools\latest' -Force"
    )
)

:: 接受许可并安装必要组件
if exist "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo [信息] 正在安装 Android SDK 组件...
    call "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%ANDROID_HOME%" "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1
)

echo.
echo [信息] 环境准备完成，开始编译...
echo.

:: 编译
call gradlew.bat assembleDebug

:: 检查结果
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ============================================
    echo   编译成功！
    echo   APK 路径: app\build\outputs\apk\debug\app-debug.apk
    echo ============================================
    echo.
    
    :: 尝试安装到手机
    set /p INSTALL="是否安装到已连接的手机？(Y/N): "
    if /i "!INSTALL!"=="Y" (
        "%ANDROID_HOME%\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
    )
) else (
    echo.
    echo ============================================
    echo   编译失败，请检查上方错误信息
    echo   提示：
    echo   1. 确保 JDK 版本是 17（当前可能是 JDK 25）
    echo   2. 下载 JDK 17: https://adoptium.net/download/
    echo   3. 用 Android Studio 打开项目编译更简单
    echo ============================================
)

pause
