package com.diting.recorder.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Pair
import android.view.Surface
import com.diting.recorder.RecorderLog

object VideoEncoderFactory {

    private const val TAG = "VideoEncoderFactory"

    @JvmStatic
    @Throws(Exception::class)
    fun createAndStart(mimeType: String, width: Int, height: Int, fps: Int, bitrate: Int): Pair<MediaCodec, Surface> {
        val codec = MediaCodec.createEncoderByType(mimeType)
        try {
            val format = createVideoFormat(mimeType, width, height, fps, bitrate)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()
            return Pair(codec, inputSurface)
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    @JvmStatic
    fun createVideoFormat(mimeType: String, width: Int, height: Int, fps: Int, bitrate: Int): MediaFormat {
        return MediaFormat.createVideoFormat(mimeType, width, height).apply {
            // 只保留所有 Android 设备强制要求支持的核心参数
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            // 剔除所有高风险的 vendor tag、B帧限制 和 低延迟标志位
            // 将底层调优权交还给系统的硬件抽象层 (HAL)
        }
    }
}
