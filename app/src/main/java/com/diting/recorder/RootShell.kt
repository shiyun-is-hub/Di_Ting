package com.diting.recorder

import java.util.concurrent.TimeUnit

/**
 * - isRootAvailable(): 申请 / 检测 Root（首次会弹出 Root 管理器确认框）
 * - execAsRoot(): 以 root 身份执行一次命令
 */
object RootShell {

    /** 申请并检测 Root 权限。 */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return false
            }
            val output = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.exitValue() == 0 && (output.contains("uid=0") || err.contains("uid=0"))
        } catch (t: Throwable) {
            false
        }
    }

    /** 以 Root 身份执行一次 shell 命令（用于拉起音频自检等短任务）。 */
    fun execAsRoot(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (finished) {
                process.exitValue() == 0
            } else {
                // 仍处于运行中（可能在等待授权或检测耗时较长），视为已发起
                true
            }
        } catch (t: Throwable) {
            false
        }
    }
}