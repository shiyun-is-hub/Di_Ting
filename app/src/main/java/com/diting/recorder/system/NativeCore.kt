package com.diting.recorder.system

/**
 * 提供内核级调度与内存控制能力（实时调度 / 锁内存 / 绑核 / 防 OOM）。
 *
 * 实现说明：真正的 native 方法声明放在 com.zero.recorder.system.NativeCore
 * （旧包名兼容占位类）中，因为预编译库 libzerocore.so 的 JNI 符号是按旧包名
 * 写死的。本类只做转发，并保证任何异常都不会冒泡到调用方。
 *
 * 所有能力均为“可选优化”：失败时静默降级，不影响录屏主流程。
 */
object NativeCore {

    private var initialized = false
    private var available = false

    init {
        available = try {
            initialized = true
            com.zero.recorder.system.NativeCore.isLoaded
        } catch (t: Throwable) {
            false
        }
    }

    /** 激活内核级实时 FIFO 调度（应在核心音视频线程中调用） */
    fun enableRealTimeScheduling(): Boolean = try {
        if (initialized) com.zero.recorder.system.NativeCore.enableRealTimeSchedulingSafe() else false
    } catch (t: Throwable) {
        false
    }

    /** 物理锁定内存页，避免换出导致的卡顿 */
    fun lockMemoryIntoRAM(): Boolean = try {
        if (initialized) com.zero.recorder.system.NativeCore.lockMemoryIntoRAMSafe() else false
    } catch (t: Throwable) {
        false
    }

    /** 绑定到性能核心（大核） */
    fun bindToPerformanceCores(): Boolean = try {
        if (initialized) com.zero.recorder.system.NativeCore.bindToPerformanceCoresSafe() else false
    } catch (t: Throwable) {
        false
    }

    /** 降低被系统低内存杀手回收的概率 */
    fun protectFromOOM() {
        try {
            if (initialized) com.zero.recorder.system.NativeCore.protectFromOOMSafe()
        } catch (t: Throwable) {
        }
    }

    fun isAvailable(): Boolean = try {
        available && com.zero.recorder.system.NativeCore.isLoaded
    } catch (t: Throwable) {
        false
    }
}