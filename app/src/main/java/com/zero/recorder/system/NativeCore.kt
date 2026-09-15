package com.zero.recorder.system

/**
 * ⚠️ 兼容占位类：请勿改动本类的包名与类名！
 *
 * 背景：本应用改名为「谛听」（包名 com.diting.recorder）之前，包名是 com.zero.recorder。
 * 随机源码携带的预编译库 libzerocore.so 中，JNI 导出符号是用【旧包名+旧类名】写死的：
 *     Java_com_zero_recorder_system_NativeCore_enableRealTimeScheduling
 *     Java_com_zero_recorder_system_NativeCore_lockMemoryIntoRAM
 *     Java_com_zero_recorder_system_NativeCore_bindToPerformanceCores
 *     Java_com_zero_recorder_system_NativeCore_protectFromOOM
 *
 * JVM 查找 native 实现时，用的正是“当前类的完整类名”拼出的符号名。
 * 因此想让这些符号被找到，就必须保留一个 包名=com.zero.recorder、类名=NativeCore 的类。
 * 这就是本类存在的唯一原因（它不参与任何业务逻辑）。
 *
 * 所有方法都带兜底：库缺失或调用失败时静默返回，不影响录屏。
 */
internal object NativeCore {

    @Volatile
    var isLoaded: Boolean = false
        private set

    init {
        isLoaded = try {
            System.loadLibrary("zerocore")
            true
        } catch (t: Throwable) {
            false
        }
    }

    // ---- 与 libzerocore.so 中旧符号对应的 native 声明（不要改名） ----
    private external fun enableRealTimeScheduling(): Boolean
    private external fun lockMemoryIntoRAM(): Boolean
    private external fun bindToPerformanceCores(): Boolean
    private external fun protectFromOOM()

    // ---- 对外安全封装（失败即降级） ----
    fun enableRealTimeSchedulingSafe(): Boolean = try {
        if (isLoaded) enableRealTimeScheduling() else false
    } catch (t: Throwable) {
        false
    }

    fun lockMemoryIntoRAMSafe(): Boolean = try {
        if (isLoaded) lockMemoryIntoRAM() else false
    } catch (t: Throwable) {
        false
    }

    fun bindToPerformanceCoresSafe(): Boolean = try {
        if (isLoaded) bindToPerformanceCores() else false
    } catch (t: Throwable) {
        false
    }

    fun protectFromOOMSafe() {
        try {
            if (isLoaded) protectFromOOM()
        } catch (t: Throwable) {
        }
    }
}