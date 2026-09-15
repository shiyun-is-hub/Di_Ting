package com.diting.recorder

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * - 拉起一次短暂的特权诊断进程（优先 Shizuku，其次 Root），对麦克风 / 系统声音做约 1.5 秒试采集
 * - 结果写入 App 外部目录 audio_check.txt，由本对象解析展示
 */
object AudioSelfCheck {

    private const val RESULT_FILE_NAME = "audio_check.txt"
    private const val LEVELS_FILE_NAME = "audio_test_levels.txt"
    private const val STOP_FILE_NAME = "audio_test.stop"
    private const val ENTRY_CLASS = "com.diting.recorder.core.RecordingOrchestrator"

    private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    fun resultFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, RESULT_FILE_NAME) }

    fun levelsFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, LEVELS_FILE_NAME) }

    fun stopFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, STOP_FILE_NAME) }

    /** 尝试拉起一次音频自检进程。返回是否成功发起。 */
    fun launch(context: Context): Boolean {
        val out = resultFile(context) ?: return false
        val apkPath = context.applicationInfo.sourceDir
        val cmd = "export CLASSPATH=$apkPath && exec app_process -Xmx256m / $ENTRY_CLASS --audio-check --check-out=${out.absolutePath}"

        // 1) 优先 Shizuku
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                ).apply { isAccessible = true }
                newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null)
                return true
            }
        } catch (t: Throwable) {
            RecorderLog.w("ZR.AudioCheck", "Shizuku launch failed: ${t.message}")
        }

        // 2) 回退 Root
        return RootShell.execAsRoot(cmd)
    }

    data class SourceResult(val ok: Boolean, val warn: Boolean, val detail: String) {
        val label: String
            get() = when {
                ok -> "✅"
                warn -> "⚠️"
                else -> "❌"
            }
    }

    data class CheckReport(val timeMs: Long, val mic: SourceResult, val sys: SourceResult)

    fun readReport(context: Context): CheckReport? {
        val f = resultFile(context) ?: return null
        if (!f.exists() || f.length() == 0L) return null
        return try {
            val map = HashMap<String, String>()
            f.readText().lineSequence().forEach { line ->
                val idx = line.indexOf('=')
                if (idx > 0) map[line.substring(0, idx)] = line.substring(idx + 1)
            }
            val mic = map["mic"] ?: return null
            val sys = map["sys"] ?: return null
            CheckReport(
                timeMs = map["time"]?.toLongOrNull() ?: f.lastModified(),
                mic = parseSource(mic),
                sys = parseSource(sys)
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun parseSource(value: String): SourceResult {
        return when {
            value.startsWith("ok:") -> SourceResult(ok = true, warn = false, detail = "${value.substringAfter("ok:")} bytes")
            value.startsWith("warn:") -> SourceResult(ok = false, warn = true, detail = "未收到音频数据")
            value.startsWith("fail:") -> SourceResult(ok = false, warn = false, detail = value.removePrefix("fail:"))
            else -> SourceResult(ok = false, warn = true, detail = value)
        }
    }

    fun formatReport(report: CheckReport): String {
        val time = DATE_FORMAT.format(Date(report.timeMs))
        val head = "上次检测：$time · 麦克风 ${report.mic.label} · 系统声音 ${report.sys.label}"
        val details = ArrayList<String>()
        if (!report.mic.ok) details.add("麦克风：${report.mic.detail.take(64)}")
        if (!report.sys.ok) details.add("系统声音：${report.sys.detail.take(64)}")
        return if (details.isEmpty()) head else "$head\n${details.joinToString("；")}"
    }

    /** 开启交互式测试会话（音频测试页）。 */
    fun launchTest(context: Context): Boolean {
        val out = resultFile(context) ?: return false
        val levels = levelsFile(context) ?: return false
        val stop = stopFile(context) ?: return false
        try { stop.delete() } catch (t: Throwable) {}
        val apkPath = context.applicationInfo.sourceDir
        val cmd = "export CLASSPATH=$apkPath && exec app_process -Xmx256m / $ENTRY_CLASS --audio-test --levels=${levels.absolutePath} --stop-flag=${stop.absolutePath} --check-out=${out.absolutePath}"

        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                ).apply { isAccessible = true }
                newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null)
                return true
            }
        } catch (t: Throwable) {
            RecorderLog.w("ZR.AudioCheck", "Shizuku launch failed: ${t.message}")
        }

        return RootShell.execAsRoot(cmd)
    }

    /** 请求停止测试（App 可直接写入自己的外部目录）。 */
    fun requestStop(context: Context) {
        try {
            stopFile(context)?.writeText("1")
        } catch (t: Throwable) {
        }
    }

    data class Levels(
        val micRms: Double,
        val sysRms: Double,
        val micState: String,
        val sysState: String,
        val micBytes: Long,
        val sysBytes: Long,
        val elapsedMs: Long,
        val done: Boolean
    )

    fun readLevels(context: Context): Levels? {
        val f = levelsFile(context) ?: return null
        if (!f.exists()) return null
        return try {
            val map = HashMap<String, String>()
            f.readText().lineSequence().forEach { line ->
                val idx = line.indexOf('=')
                if (idx > 0) map[line.substring(0, idx)] = line.substring(idx + 1)
            }
            Levels(
                micRms = map["mic"]?.toDoubleOrNull() ?: 0.0,
                sysRms = map["sys"]?.toDoubleOrNull() ?: 0.0,
                micState = map["micState"] ?: "unknown",
                sysState = map["sysState"] ?: "unknown",
                micBytes = map["micBytes"]?.toLongOrNull() ?: 0L,
                sysBytes = map["sysBytes"]?.toLongOrNull() ?: 0L,
                elapsedMs = map["elapsed"]?.toLongOrNull() ?: 0L,
                done = map["done"] == "1"
            )
        } catch (t: Throwable) {
            null
        }
    }
}
