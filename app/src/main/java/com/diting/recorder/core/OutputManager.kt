package com.diting.recorder.core

import com.diting.recorder.RecorderLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object OutputManager {
    private const val TAG = "ZR.Output"

    fun buildOutputPath(outputDirPath: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var outputDir = File(outputDirPath)
        if (!outputDir.exists()) outputDir.mkdirs()
        if (!outputDir.exists() || !outputDir.isDirectory) {
            RecorderLog.w(TAG, "Output dir unavailable: $outputDirPath, fallback to default")
            outputDir = File(com.diting.recorder.RecorderConfig.OUTPUT_DIR)
            if (!outputDir.exists()) outputDir.mkdirs()
        }
        return "${outputDir.absolutePath}/Rec_$timestamp.mp4"
    }

    fun cleanupFailedOutput(outputPath: String) {
        try {
            val outputFile = File(outputPath)
            val parentDir = outputFile.parentFile
            parentDir?.let {
                it.listFiles { f -> f.name.startsWith(".tmp_part_") }?.forEach { f -> f.delete() }
            }
            outputFile.delete()
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }

    fun cleanupResidualEnvironment(outputDirPath: String) {
        try {
            val dir = File(outputDirPath)
            if (dir.exists() && dir.isDirectory) {
                val tmps = dir.listFiles { f -> f.name.startsWith(".tmp_part_") }
                tmps?.forEach { f ->
                    if (f.length() > 1024) {
                        val newName = f.name.replace(".tmp_part_", "recovered_part_") + ".mp4"
                        f.renameTo(File(dir, newName))
                    } else {
                        f.delete()
                    }
                }
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }
}
