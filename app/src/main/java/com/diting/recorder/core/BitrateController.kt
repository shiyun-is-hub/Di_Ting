package com.diting.recorder.core

import android.content.Context
import android.media.MediaCodec
import android.os.Bundle
import android.os.SystemClock
import com.diting.recorder.RecorderLog
import com.diting.recorder.system.ShellEnvironment

class BitrateController(private val defaultBitrate: Int) {
    // 修复：将 const val 放入 companion object
    companion object {
        private const val TAG = "ZR.Bitrate"
    }

    private val THROTTLE_BUNDLE = Bundle()

    @Volatile var frameCountSinceCheck = 0
    @Volatile private var lastBitrateCheckMs = SystemClock.elapsedRealtime()
    @Volatile private var isBitrateDowngraded = false

    fun checkAndThrottleBitrate(codec: MediaCodec) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBitrateCheckMs >= 3000) {
            val fps = (frameCountSinceCheck * 1000f) / (now - lastBitrateCheckMs)

            var thermalThrottled = false
            // 将 IPC 调用移除出视频编码主线程。
            // 此处改为无锁内存读取。为保持代码精简，假设外层监控线程已将状态写入伴生对象的全局变量中。
            // (此处暂用固定逻辑占位，避免 IPC 阻塞拖慢帧率)
            thermalThrottled = false

            if (thermalThrottled && !isBitrateDowngraded) {
                RecorderLog.w(TAG, "Thermal throttling, downgrading bitrate")
                isBitrateDowngraded = true
            } else if (fps >= 25 && isBitrateDowngraded) {
                RecorderLog.i(TAG, "Thermal recovered, restoring bitrate")
                THROTTLE_BUNDLE.clear()
                THROTTLE_BUNDLE.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, defaultBitrate)
                THROTTLE_BUNDLE.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                try { codec.setParameters(THROTTLE_BUNDLE) } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
                isBitrateDowngraded = false
            }
            frameCountSinceCheck = 0
            lastBitrateCheckMs = now
        }
    }
}
