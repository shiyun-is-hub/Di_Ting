package com.diting.recorder.basic

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.MediaStore
import com.diting.recorder.RecorderConfig
import com.diting.recorder.RecorderLog
import com.diting.recorder.RecorderOptions
import com.diting.recorder.core.BitrateController
import com.diting.recorder.core.PtsNormalizer
import com.diting.recorder.gl.GlFrameRenderer
import com.diting.recorder.media.SegmentedMp4Muxer
import com.diting.recorder.media.VideoEncoderFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于 MediaProjection 的免权限录制：
 * - 系统授权弹窗 → VirtualDisplay → OpenGL → 硬编码器 → MP4
 * - 音频：系统声音（播放捕获，Android 10+）+ 麦克风，按设置开关，双音轨
 * - 完成后通过 MediaStore 导出到相册可见目录
 */
class BasicRecordingEngine(
    private val context: Context,
    private val projection: MediaProjection,
    private val options: RecorderOptions,
    private val recordingActive: AtomicBoolean,
    private val baseName: String,
    private val onFinished: (Boolean, String) -> Unit
) {
    private val TAG = "ZR.Basic"
    private val ptsNormalizer = PtsNormalizer()

    private class AudioPipeline(
        val label: String,
        val record: AudioRecord,
        val codec: MediaCodec,
        val sampleRate: Int,
        val channels: Int,
        val primary: Boolean
    ) {
        val eosLatch = CountDownLatch(1)
    }

    fun start() {
        try {
            runInternal()
        } catch (t: Throwable) {
            RecorderLog.e(TAG, "Basic engine failed: ${t.javaClass.simpleName} - ${t.message}")
            recordingActive.set(false)
            try { onFinished(false, t.message ?: "未知错误") } catch (e: Throwable) {}
        }
    }

    private fun runInternal() {
        // ---------- 屏幕尺寸与目标分辨率 ----------
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        display.getRealMetrics(metrics)
        val target = computeTargetSize(metrics.widthPixels, metrics.heightPixels, options.quality)
        val targetW = target.first
        val targetH = target.second

        // ---------- 工作目录 ----------
        val workDir = File(context.getExternalFilesDir(null), "basic_tmp")
        if (!workDir.exists()) workDir.mkdirs()
        workDir.listFiles()?.forEach { it.delete() }

        // ---------- 视频编码器 ----------
        var videoCodec: MediaCodec? = null
        var videoMime = ""
        for (mime in arrayOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)) {
            try {
                val fmt = MediaFormat.createVideoFormat(mime, targetW, targetH)
                if (MediaCodecList(MediaCodecList.ALL_CODECS).findEncoderForFormat(fmt) != null) {
                    videoCodec = MediaCodec.createEncoderByType(mime)
                    videoMime = mime
                    break
                }
            } catch (e: Exception) {
            }
        }
        if (videoCodec == null) {
            onFinished(false, "未找到可用的视频编码器")
            return
        }

        // ---------- 音频管线 ----------
        val pipelines = ArrayList<AudioPipeline>()
        if (options.sysAudioEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val rec = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(cfg)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(48000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(65536)
                    .build()
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("播放捕获初始化失败")
                }
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                pipelines.add(AudioPipeline("系统声音", rec, codec, 48000, 2, pipelines.isEmpty()))
            } catch (t: Throwable) {
                RecorderLog.w(TAG, "Playback capture unavailable: ${t.message}")
            }
        }
        if (options.micEnabled) {
            try {
                var rec: AudioRecord? = null
                var channels = 2
                for (mask in intArrayOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)) {
                    val minBuf = AudioRecord.getMinBufferSize(48000, mask, AudioFormat.ENCODING_PCM_16BIT)
                    val r = AudioRecord(android.media.MediaRecorder.AudioSource.MIC, 48000, mask, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf * 4, 16384))
                    if (r.state == AudioRecord.STATE_INITIALIZED) {
                        rec = r
                        channels = if (mask == AudioFormat.CHANNEL_IN_MONO) 1 else 2
                        break
                    } else {
                        try { r.release() } catch (e: Exception) {}
                    }
                }
                if (rec == null) throw IllegalStateException("麦克风初始化失败")
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                pipelines.add(AudioPipeline("麦克风", rec, codec, 48000, channels, pipelines.isEmpty()))
            } catch (t: Throwable) {
                RecorderLog.w(TAG, "Mic unavailable: ${t.message}")
            }
        }

        // ---------- 封装器 ----------
        val expectedTracks = 1 + pipelines.size
        val muxer = SegmentedMp4Muxer(workDir.absolutePath, baseName, expectedTracks)

        // ---------- 视频回调 ----------
        val videoCbThread = HandlerThread("BasicVideoCb", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        val videoEosLatch = CountDownLatch(1)
        val bitrateController = BitrateController(8_000_000)
        val videoTrackIndex = arrayOf(-1)

        videoCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {}
            override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                bitrateController.checkAndThrottleBitrate(mc)
                val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (info.size > 0 && videoTrackIndex[0] >= 0 && !isEos) {
                    ptsNormalizer.normalizeTimeBase(info)
                    muxer.writeSampleData(videoTrackIndex[0], mc.getOutputBuffer(index), info)
                }
                mc.releaseOutputBuffer(index, false)
                if (isEos) videoEosLatch.countDown()
            }

            override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
                videoTrackIndex[0] = muxer.addTrack(format)
            }

            override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
                RecorderLog.e(TAG, "Video codec error: ${e.message}")
                if (!e.isRecoverable && !e.isTransient) recordingActive.set(false)
            }
        }, Handler(videoCbThread.looper))

        val videoFormat = VideoEncoderFactory.createVideoFormat(
            videoMime, targetW, targetH, options.fps,
            Math.max((targetW * targetH * options.fps * 0.15f).toInt(), 4_000_000)
        )
        try {
            val vc = videoCodec.codecInfo.getCapabilitiesForType(videoMime).videoCapabilities
            val tb = videoFormat.getInteger(MediaFormat.KEY_BIT_RATE)
            val safe = vc?.bitrateRange?.clamp(tb) ?: tb
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, safe)
        } catch (e: Exception) {
        }
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoCodec.createInputSurface()

        // ---------- 音频回调 ----------
        val dummySilence = ByteArray(2048)
        for (session in pipelines) {
            val cbThread = HandlerThread("BasicAudioCb-${session.label}", Process.THREAD_PRIORITY_URGENT_AUDIO).also { it.start() }
            val trackIndex = arrayOf(-1)
            val sampleCount = arrayOf(0L)

            session.codec.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
                    if (!recordingActive.get()) {
                        mc.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        try { session.record.stop() } catch (e: Exception) {}
                        return
                    }
                    val inBuf = mc.getInputBuffer(index)!!
                    val read = session.record.read(inBuf, Math.min(inBuf.capacity(), 4096))
                    if (read > 0) {
                        var pts = (sampleCount[0] * 1000000L) / session.sampleRate
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val ts = android.media.AudioTimestamp()
                            if (session.record.getTimestamp(ts, android.media.AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                                val hwPts = (ts.nanoTime - ptsNormalizer.globalStartNs) / 1000L
                                if (hwPts > 0 && Math.abs(hwPts - pts) < 50000) pts = hwPts
                            }
                        }
                        sampleCount[0] = sampleCount[0] + read / (session.channels * 2)
                        if (session.primary) {
                            val currentSysNs = System.nanoTime() - ptsNormalizer.globalStartNs
                            val newOffset = currentSysNs - (pts * 1000L)
                            if (ptsNormalizer.audioTimeOffsetNs == 0L || Math.abs(ptsNormalizer.audioTimeOffsetNs - newOffset) > 500_000_000L) {
                                ptsNormalizer.audioTimeOffsetNs = newOffset
                            } else {
                                ptsNormalizer.audioTimeOffsetNs = (ptsNormalizer.audioTimeOffsetNs * 19 + newOffset) / 20
                            }
                        }
                        mc.queueInputBuffer(index, 0, read, pts, 0)
                    } else {
                        val fallbackPts = (sampleCount[0] * 1000000L) / session.sampleRate
                        inBuf.clear()
                        inBuf.put(dummySilence)
                        sampleCount[0] = sampleCount[0] + dummySilence.size / (session.channels * 2)
                        mc.queueInputBuffer(index, 0, dummySilence.size, fallbackPts, 0)
                        try { Thread.sleep(5) } catch (e: Exception) {}
                    }
                }

                override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.size > 0 && trackIndex[0] >= 0 && !isEos) {
                        ptsNormalizer.normalizeTimeBase(info)
                        muxer.writeSampleData(trackIndex[0], mc.getOutputBuffer(index), info)
                    }
                    mc.releaseOutputBuffer(index, false)
                    if (isEos) session.eosLatch.countDown()
                }

                override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
                    trackIndex[0] = muxer.addTrack(format)
                }

                override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
                    RecorderLog.e(TAG, "Audio codec error: ${e.message}")
                    if (!e.isRecoverable && !e.isTransient) recordingActive.set(false)
                }
            }, Handler(cbThread.looper))

            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, session.sampleRate, session.channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            session.codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        // ---------- 渲染与采集 ----------
        muxer.start()
        val renderer = GlFrameRenderer(targetW, targetH)
        renderer.initialize(inputSurface)
        renderer.updateSourceSize(targetW, targetH)

        val virtualDisplay = projection.createVirtualDisplay(
            "DitingBasic", targetW, targetH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, renderer.inputSurface, null, null
        )

        videoCodec.start()
        for (p in pipelines) {
            p.codec.start()
            p.record.startRecording()
        }

        // ---------- 帧泵 ----------
        val initialRotation = display.rotation
        while (recordingActive.get()) {
            val rot = display.rotation
            var rotationFix = (initialRotation - rot) * 90f
            if (rotationFix < 0) rotationFix += 360f
            val ptsNs = System.nanoTime() - ptsNormalizer.globalStartNs
            renderer.awaitAndDraw(rotationFix, options.fps, ptsNs)
        }

        // ---------- 收尾 ----------
        try { videoCodec.signalEndOfInputStream() } catch (e: Exception) {}
        for (p in pipelines) {
            try { p.record.stop() } catch (e: Exception) {}
        }
        try { videoEosLatch.await(10, TimeUnit.SECONDS) } catch (e: InterruptedException) {}
        for (p in pipelines) {
            try { p.eosLatch.await(5, TimeUnit.SECONDS) } catch (e: InterruptedException) {}
        }
        try { virtualDisplay.release() } catch (e: Exception) {}
        try { renderer.release() } catch (e: Exception) {}
        for (p in pipelines) {
            try { p.record.release() } catch (e: Exception) {}
            try { p.codec.stop() } catch (e: Exception) {}
            try { p.codec.release() } catch (e: Exception) {}
        }
        try { videoCodec.stop() } catch (e: Exception) {}
        try { videoCodec.release() } catch (e: Exception) {}
        try { videoCbThread.quitSafely() } catch (e: Exception) {}

        muxer.stopAndRelease()

        // ---------- 导出到媒体库 ----------
        val merged = File(muxer.getFinalOutputPath())
        if (!merged.exists() || merged.length() < 1024) {
            onFinished(false, "录制文件生成失败")
            return
        }

        val relativeDir = deriveRelativeDir(options.outputDir)
        val saved = try {
            exportToMediaStore(merged, baseName, relativeDir)
        } catch (t: Throwable) {
            null
        }
        try { merged.delete() } catch (e: Exception) {}
        try { workDir.listFiles()?.forEach { it.delete() } } catch (e: Exception) {}

        if (saved != null) {
            onFinished(true, saved)
        } else {
            onFinished(false, "导出到相册失败")
        }
    }

    private fun computeTargetSize(screenW: Int, screenH: Int, quality: String): Pair<Int, Int> {
        if (quality == RecorderConfig.QUALITY_SCREEN) {
            return Pair(screenW and -16, screenH and -16)
        }
        val limit = when (quality) {
            RecorderConfig.QUALITY_720P -> 720
            RecorderConfig.QUALITY_360P -> 360
            else -> 1080
        }
        val shortEdge = Math.min(screenW, screenH)
        val scale = if (shortEdge > limit) limit.toFloat() / shortEdge else 1f
        return Pair((screenW * scale).toInt() and -16, (screenH * scale).toInt() and -16)
    }

    private fun deriveRelativeDir(outputDir: String): String {
        val prefix = "/storage/emulated/0/"
        return if (outputDir.startsWith(prefix)) {
            val rel = outputDir.removePrefix(prefix).trim('/')
            if (rel.isEmpty()) "Movies/Diting" else rel
        } else {
            "Movies/Diting"
        }
    }

    private fun exportToMediaStore(src: File, name: String, relativeDir: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("无法打开输出流")
            out.use { o ->
                src.inputStream().use { input ->
                    input.copyTo(o)
                }
            }
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri.toString()
        } catch (t: Throwable) {
            try { resolver.delete(uri, null, null) } catch (e: Throwable) {}
            null
        }
    }
}