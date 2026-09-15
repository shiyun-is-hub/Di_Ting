package com.diting.recorder

import android.content.Context
import java.io.File
import java.nio.file.Files
import rikka.shizuku.Shizuku

object RecorderLauncher {

    @JvmStatic
    fun startRecording(context: Context): Boolean {
        return try {
            killExisting()
            val apkPath = context.applicationInfo.sourceDir
            val entryClass = "com.diting.recorder.core.RecordingOrchestrator"
            // 将设置页选择的画质/帧率/音频/保存位置传递给录制进程
            val quality = RecorderSettings.getQuality(context)
            val fps = RecorderSettings.getFps(context)
            val micEnabled = RecorderSettings.isMicEnabled(context)
            val sysAudioEnabled = RecorderSettings.isSysAudioEnabled(context)
            val outputDir = RecorderSettings.getOutputDir(context).replace("'", "")
            val cmd = "export CLASSPATH=$apkPath && exec app_process -Xmx256m / $entryClass --quality=$quality --fps=$fps --mic=${if (micEnabled) "on" else "off"} --sysaudio=${if (sysAudioEnabled) "on" else "off"} --out='$outputDir'"

            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }

            newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null)
            true
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught", e)
            false
        }
    }

    @JvmStatic
    private fun killExisting() {
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }

            val p = newProcessMethod.invoke(null, arrayOf("sh", "-c", "pkill -f 'com.diting.recorder.core.RecordingOrchestrator'"), null, null) as? Process
            p?.waitFor()
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught", e)
        }
    }

    @JvmStatic
    fun forceStop() {
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }

            newProcessMethod.invoke(null, arrayOf("sh", "-c", "touch /data/local/tmp/diting_recorder.stop"), null, null)

            for (i in 0 until 4) {
                Thread.sleep(500) /* FIXME: 阻塞式休眠，建议重构为协程 delay() 或使用 ReentrantLock.Condition 同步机制 */
                if (!File("/data/local/tmp/diting_recorder.pid").exists()) return
            }

            val pidFile = File("/data/local/tmp/diting_recorder.pid")
            if (pidFile.exists()) {
                val pid = String(Files.readAllBytes(pidFile.toPath())).trim()
                newProcessMethod.invoke(null, arrayOf("sh", "-c", "kill -9 $pid"), null, null)
                pidFile.delete()
                File("/data/local/tmp/diting_recorder.stop").delete()
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught", e)
        }
    }
}