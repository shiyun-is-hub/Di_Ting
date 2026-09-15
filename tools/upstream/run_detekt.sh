#!/system/bin/sh
export GRADLE_USER_HOME="$PWD/.gradle_local"
export GRADLE_OPTS="-Dorg.gradle.native=false"

# RV2IDE 环境检测与回退配置
IDE_HOME="/data/user/0/com.tom.rv2ide/files"
if [ -d "$IDE_HOME" ]; then
    echo "[*] Detected RV2IDE environment"
    export ANDROID_HOME="$IDE_HOME/home/android-sdk"
    export JAVA_HOME="$IDE_HOME/usr"
    GRADLE_BIN=$(find "$IDE_HOME/home/.gradle/wrapper/dists" -type f -name "gradle" 2>/dev/null | sort -V | tail -n 1)
    export PATH="$(dirname "$GRADLE_BIN"):$JAVA_HOME/bin:$(ls -d "$ANDROID_HOME/build-tools/"*/ 2>/dev/null | sort -V | tail -n 1 | sed 's/\/$//'):/system/bin:$PATH"
else
    echo "[*] Using standard environment"
    if [ -z "$ANDROID_HOME" ] || [ -z "$JAVA_HOME" ]; then
        echo "[E] ANDROID_HOME or JAVA_HOME not set!"
        exit 1
    fi
    export PATH="$JAVA_HOME/bin:/system/bin:$PATH"
fi

echo "[*] 开始执行 Detekt 代码规范扫描..."
sh ./gradlew detekt --daemon
