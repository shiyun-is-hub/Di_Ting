#!/system/bin/sh
# manage.sh 优化版：run 通过pm获取已安装APK路径
set -euo pipefail

ACTION=${1:-run}
APP_PACKAGE="com.diting.recorder"
ENTRY_CLASS="com.diting.recorder.core.RecordingOrchestrator"

# RV2IDE 环境检测与回退配置
IDE_HOME="/data/user/0/com.tom.rv2ide/files"
if [ -d "$IDE_HOME" ]; then
    echo "[*] Detected RV2IDE environment"
    RV2_USR="$IDE_HOME/usr"
    export ANDROID_HOME="$IDE_HOME/home/android-sdk"
    export JAVA_HOME="$RV2_USR"
    GRADLE_BIN=$(find "$IDE_HOME/home/.gradle/wrapper/dists" -type f -name "gradle" 2>/dev/null | sort -V | tail -n 1)
    BUILD_TOOLS=$(ls -d "$ANDROID_HOME/build-tools/"*/ 2>/dev/null | sort -V | tail -n 1 || true)
    BUILD_TOOLS_DIR=${BUILD_TOOLS%/}
    export PATH="$(dirname "$GRADLE_BIN"):$RV2_USR/bin:$BUILD_TOOLS_DIR:/system/bin:$PATH"
else
    echo "[*] Using standard environment (ANDROID_HOME=$ANDROID_HOME, JAVA_HOME=$JAVA_HOME)"
    if [ -z "$ANDROID_HOME" ] || [ -z "$JAVA_HOME" ]; then
        echo "[E] ANDROID_HOME or JAVA_HOME not set!"
        exit 1
    fi
    BUILD_TOOLS=$(ls -d "$ANDROID_HOME/build-tools/"*/ 2>/dev/null | sort -V | tail -n 1 || true)
    BUILD_TOOLS_DIR=${BUILD_TOOLS%/}
    export PATH="$JAVA_HOME/bin:$BUILD_TOOLS_DIR:/system/bin:$PATH"
fi

# 编译APK
build_apk() {
    echo "[*] 开始编译 Diting Debug APK..."
    gradle assembleDebug --daemon --parallel -x lint
    APK_PATH=$(find ./app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n 1)
    if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
        echo "[E] 编译失败，未找到输出APK"
        exit 1
    fi
    echo "[+] 编译完成：$APK_PATH"
}

# 安装APK
install_apk() {
    APK_PATH=$(find ./app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n 1)
    if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
        echo "[W] 未检测到本地APK，自动执行编译"
        build_apk
        APK_PATH=$(find ./app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n 1)
    fi
    echo "[*] 安装应用至系统：$APP_PACKAGE"
    pm install -r "$APK_PATH"
    echo "[+] 安装完成"
}

# 核心优化：run_headless 通过pm path读取已安装APK，优先使用系统内安装包
run_headless() {
    # 1. 先通过pm查询已安装应用的APK路径
    PM_RAW=$(pm path "$APP_PACKAGE" 2>/dev/null || true)
    # 提取 /data/app/xxx/base.apk 路径
    PM_APK=$(echo "$PM_RAW" | sed -n 's/^package://p')

    if [ -n "$PM_APK" ] && [ -f "$PM_APK" ]; then
        echo "[*] 从系统已安装包读取APK路径：$PM_APK"
        APK_PATH="$PM_APK"
    else
        # 无安装包则回退本地编译逻辑
        echo "[W] 系统未检测到已安装 $APP_PACKAGE，使用本地编译APK"
        APK_PATH=$(find ./app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -n 1)
        if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
            echo "[E] 无已安装包、本地也无编译产物，请先执行 install/build"
            exit 1
        fi
    fi

    # 清理残留进程
    echo "[*] 终止残留录制引擎进程"
    pkill -f "$ENTRY_CLASS" 2>/dev/null || true
    sleep 0.2

    # app_process 启动无头程序
    echo "[*] 启动 Headless 录制引擎 entry=$ENTRY_CLASS"
    export CLASSPATH="$APK_PATH"
    exec app_process -Xmx256m / "$ENTRY_CLASS" "$@"
}

# 命令分发
case "$ACTION" in
    build)
        build_apk
        ;;
    install)
        install_apk
        ;;
    run)
        shift
        run_headless "$@"
        ;;
    all)
        build_apk
        install_apk
        shift
        run_headless "$@"
        ;;
    *)
        echo "===== Diting 管理脚本 ====="
        echo "用法: sh manage.sh [build|install|run|all] [启动参数]"
        echo "  build   仅编译debug安装包"
        echo "  install 编译并安装到当前设备"
        echo "  run     优先读取系统已安装APK运行无头引擎"
        echo "  all     编译+安装+直接运行"
        exit 1
        ;;
esac
