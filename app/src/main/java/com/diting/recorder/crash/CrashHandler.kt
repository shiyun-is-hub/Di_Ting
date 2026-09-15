package com.diting.recorder.crash

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 在任意线程发生未捕获异常时：
 * 1. 收集异常堆栈 + 设备型号（Build.MODEL）+ 系统版本（Build.VERSION.RELEASE）+ 应用版本号
 * 2. 写入 context.getExternalFilesDir(null)/crash_log.txt
 * 3. 启动 CrashActivity 展示日志（提供复制 / 邮件反馈）
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        const val LOG_FILE_NAME = "crash_log.txt"
        private const val TAG = "ZR.Crash"

        @Volatile
        private var installed = false

        /** 安装全局捕获（重复调用安全） */
        @JvmStatic
        fun install(context: Context) {
            if (installed) return
            installed = true
            val app = context.applicationContext
            val handler = CrashHandler(app, Thread.getDefaultUncaughtExceptionHandler())
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        /** 崩溃日志文件 */
        @JvmStatic
        fun logFile(context: Context): File =
            File(context.getExternalFilesDir(null), LOG_FILE_NAME)

        /** 读取已有的崩溃日志（不存在返回 null） */
        @JvmStatic
        fun readLog(context: Context): String? {
            return try {
                val f = logFile(context)
                if (f.exists() && f.length() > 0) f.readText() else null
            } catch (t: Throwable) {
                null
            }
        }

        /** 清除崩溃日志 */
        @JvmStatic
        fun clearLog(context: Context) {
            try {
                val f = logFile(context)
                if (f.exists()) f.delete()
            } catch (t: Throwable) {
            }
        }
    }

    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 防止处理过程中再次崩溃导致递归
        if (!handling.compareAndSet(false, true)) {
            return
        }

        val logText = try {
            buildCrashLog(thread, throwable)
        } catch (t: Throwable) {
            "日志收集失败：${t.message}\n\n原始异常：${throwable.message}"
        }

        try {
            logFile(context).writeText(logText)
        } catch (t: Throwable) {
        }

        try {
            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(CrashActivity.EXTRA_LOG_TEXT, logText)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            // 启动失败则交给系统默认处理
        }

        // 结束当前进程，让系统干净地重启应用
        try {
            Thread.sleep(300)
        } catch (t: InterruptedException) {
        }
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(10)
    }

    /** 组装崩溃日志文本 */
    @SuppressLint("HardwareIds")
    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        val stackTrace = sw.toString()
        pw.close()

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val appVersion = appVersionName()
        val versionCode = appVersionCode()

        return buildString {
            appendLine("====== 谛听录屏 · 崩溃报告 ======")
            appendLine("时间：$time")
            appendLine("设备型号：${Build.MODEL}")
            appendLine("品牌：${Build.BRAND}（制造商：${Build.MANUFACTURER}）")
            appendLine("Android 版本：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
            appendLine("App 版本：$appVersion（versionCode $versionCode）")
            appendLine("包名：${context.packageName}")
            appendLine("崩溃线程：${thread.name}")
            appendLine()
            appendLine("------ 异常信息 ------")
            appendLine("${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            appendLine("------ 堆栈 ------")
            append(stackTrace)
            appendLine()
            appendLine("====== 报告结束 ======")
        }
    }

    private fun appVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
    } catch (t: Throwable) {
        "未知"
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Long = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            pi.versionCode.toLong()
        }
    } catch (t: Throwable) {
        -1L
    }
}