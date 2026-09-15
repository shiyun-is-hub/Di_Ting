# ==== [体积优化] R8 保留规则 ====
# app_process 外部入口（类名被字符串引用，不能混淆/裁剪）
-keep class com.diting.recorder.core.RecordingOrchestrator { *; }
# Shizuku 库通过反射调用 newProcess 等方法
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# ==== [体积优化] 关键：禁止混淆 native 方法所在类 ====
# JNI 函数名是写死的 Java_<包名>_<类名>_<方法名>，混淆会导致 UnsatisfiedLinkError
-keepclasseswithmembernames class ** { native <methods>; }
-keep class com.diting.recorder.system.NativeCore { *; }
-keep class com.diting.recorder.** { native <methods>; }
-keepclasseswithmembers,allowoptimization class com.diting.recorder.** { native <methods>; }

# ==== [改名兼容] 旧包名 JNI 桥接类：包名/类名必须原样保留 ====
# libzerocore.so 的符号是 Java_com_zero_recorder_system_NativeCore_*，
# 这个类的完整限定名决定了 JVM 能否找到 native 实现，禁止混淆与裁剪。
-keep class com.zero.recorder.system.NativeCore { *; }
-keepnames class com.zero.recorder.system.NativeCore
-dontwarn com.zero.recorder.system.**

# ==== [崩溃捕获] 组件不能被裁剪/混淆 ====
-keep class com.diting.recorder.crash.CrashActivity { *; }
-keep class com.diting.recorder.crash.CrashHandler { *; }
