package com.diting.recorder.core

import android.content.Context
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface
import com.diting.recorder.RecorderLog
import com.diting.recorder.RecorderOptions
import com.diting.recorder.audio.AudioCaptureFactory
import com.diting.recorder.capture.DisplayCaptureController
import com.diting.recorder.gl.GlFrameRenderer
import com.diting.recorder.media.SegmentedMp4Muxer
import com.diting.recorder.media.VideoEncoderFactory
import com.diting.recorder.system.NativeCore
import com.diting.recorder.system.RecorderResourceManager
import com.diting.recorder.system.ShellEnvironment
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RecordingEngine(
    private val shellContext: Context,
    private val displayController: DisplayCaptureController,
    private val recordingActive: AtomicBoolean,
    private val options: RecorderOptions
) {
    private val TAG = "ZR.Engine"
    private val ptsNormalizer = PtsNormalizer()

    /** 双音轨 一路音频管线（系统声音 / 麦克风 各一条）。 */
    private class AudioPipeline(
        val label: String,
        val session: AudioCaptureFactory.AudioCaptureSession,
        val codec: MediaCodec,
        val primary: Boolean
    ) {
        val eosLatch = CountDownLatch(1)
    }

    fun start(initialRotation: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        val outputPath = OutputManager.buildOutputPath(options.outputDir)
        val candidates = CandidateSelector.buildVideoCandidates(displayController, options.fps, options.quality)
        if (candidates.isEmpty()) return

        for (candidate in candidates) {
            ptsNormalizer.reset()
            val success = attemptRecording(initialRotation, outputPath, candidate)
            if (success) {
                RecorderLog.i(TAG, "Saved to $outputPath")
                publishToGallery(outputPath, options.outputDir)
                return
            }
            OutputManager.cleanupFailedOutput(outputPath)
            if (!recordingActive.get()) break
        }
    }

    /**
     * 将录制完成的文件发布到系统相册。
     * 优先走 MediaStore 导出（相册立即可见）；失败则回退为通知媒体库扫描。
     */
    private fun publishToGallery(outputPath: String, outputDir: String) {
        val src = File(outputPath)
        if (!src.exists() || src.length() <= 0) return
        val displayName = src.name
        val relativeDir = MediaStoreExporter.toRelativeDir(outputDir)

        val context = try {
            ShellEnvironment.getShellContext()
        } catch (t: Throwable) {
            null
        }
        if (context == null) {
            RecorderLog.w(TAG, "Shell context unavailable, skip gallery publish")
            return
        }

        val exported = try {
            MediaStoreExporter.exportViaMediaStore(context, src, displayName, relativeDir)
        } catch (t: Throwable) {
            RecorderLog.w(TAG, "MediaStore export failed: ${t.message}")
            null
        }

        if (exported != null) {
            try { src.delete() } catch (e: Exception) {}
            return
        }

        MediaStoreExporter.scanFile(context, src)
    }

    /** 创建一路音频管线；失败时仅跳过该路（不影响录制主体）。 */
    private fun addAudioPipeline(
        list: MutableList<AudioPipeline>,
        resourceManager: RecorderResourceManager,
        label: String,
        creator: () -> AudioCaptureFactory.AudioCaptureSession
    ) {
        try {
            val session = creator()
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            resourceManager.addAudioSession(session)
            resourceManager.addAudioCodec(codec)
            list.add(AudioPipeline(label, session, codec, list.isEmpty()))
        } catch (e: Exception) {
            RecorderLog.w(TAG, "Audio ($label) unavailable, skipped: ${e.message}")
        }
    }

    private fun attemptRecording(initialRotation: Int, outputPath: String, candidate: CandidateSelector.VideoCandidate): Boolean {
        RecorderResourceManager().use { resourceManager ->
            var videoCodec: MediaCodec?
            var muxer: SegmentedMp4Muxer?
            var renderer: GlFrameRenderer?
            var inputSurface: Surface?

            val videoEosLatch = CountDownLatch(1)
            val bitrateController = BitrateController(8_000_000)
            val audioPipelines = ArrayList<AudioPipeline>()
            val dummySilence = ByteArray(2048)

            try {
                videoCodec = MediaCodec.createEncoderByType(candidate.mimeType)
                resourceManager.addVideoCodec(videoCodec)

                // ---------- 音频管线（按设置项：系统声音 / 麦克风，可同时开启 → 双音轨） ----------
                if (android.os.Build.VERSION.SDK_INT == 30) {
                    try {
                        Runtime.getRuntime().exec("am start -n com.android.shell/.HeapDumpActivity")
                        Thread.sleep(250) // 创建 AudioRecord 前先抢占前台焦点
                    } catch (e: Exception) {
                    }
                }
                if (options.sysAudioEnabled) {
                    addAudioPipeline(audioPipelines, resourceManager, "系统声音") {
                        AudioCaptureFactory.createSystemAudioSession()
                    }
                }
                if (options.micEnabled) {
                    addAudioPipeline(audioPipelines, resourceManager, "麦克风") {
                        AudioCaptureFactory.createMicSession()
                    }
                }

                val expectedTracks = 1 + audioPipelines.size
                val outputFile = File(outputPath)
                val outputDir = outputFile.parent!!
                val baseName = outputFile.nameWithoutExtension
                muxer = SegmentedMp4Muxer(outputDir, baseName, expectedTracks)
                resourceManager.addMuxer(muxer)

                val videoCbThread = HandlerThread("VideoCodecCb", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
                Handler(videoCbThread.looper).post {
                    if (NativeCore.isAvailable()) {
                        NativeCore.enableRealTimeScheduling()
                        NativeCore.bindToPerformanceCores()
                    }
                }
                resourceManager.addThread(videoCbThread)

                val videoTrackIndex = arrayOf(-1)
                val finalMuxer = muxer
                videoCodec.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {}
                    override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        bitrateController.checkAndThrottleBitrate(mc)
                        val isVideoEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 && videoTrackIndex[0] >= 0 && !isVideoEos) {
                            ptsNormalizer.normalizeTimeBase(info)
                            finalMuxer.writeSampleData(videoTrackIndex[0], mc.getOutputBuffer(index), info)
                        }
                        mc.releaseOutputBuffer(index, false)
                        if (isVideoEos) videoEosLatch.countDown()
                    }

                    override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
                        videoTrackIndex[0] = finalMuxer.addTrack(format)
                    }

                    override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
                        RecorderLog.e(TAG, "Video Codec Fatal Error: ${e.message}")
                        if (!e.isRecoverable && !e.isTransient) recordingActive.set(false)
                    }
                }, Handler(videoCbThread.looper))

                val videoFormat = VideoEncoderFactory.createVideoFormat(
                    candidate.mimeType, candidate.width, candidate.height,
                    candidate.fps, Math.max((candidate.width * candidate.height * candidate.fps * 0.15f).toInt(), 4_000_000)
                )
                try {
                    val vc = videoCodec.codecInfo.getCapabilitiesForType(candidate.mimeType).videoCapabilities
                    val targetBitrate = videoFormat.getInteger(MediaFormat.KEY_BIT_RATE)
                    val safeBitrate = vc?.bitrateRange?.clamp(targetBitrate) ?: targetBitrate
                    videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, safeBitrate)
                } catch (e: Exception) {
                    com.diting.recorder.RecorderLog.w("ZR.Engine", "Bitrate clamp skipped: ${e.message}")
                }

                videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = videoCodec.createInputSurface()

                // ---------- 各路音频回调 ----------
                for (pipeline in audioPipelines) {
                    val session = pipeline.session
                    val codec = pipeline.codec
                    val isPrimary = pipeline.primary
                    val label = pipeline.label
                    val eosLatch = pipeline.eosLatch

                    val audioCbThread = HandlerThread("AudioCb-$label", Process.THREAD_PRIORITY_URGENT_AUDIO).also { it.start() }
                    Handler(audioCbThread.looper).post {
                        if (NativeCore.isAvailable()) {
                            NativeCore.enableRealTimeScheduling()
                            NativeCore.bindToPerformanceCores()
                        }
                    }
                    resourceManager.addThread(audioCbThread)

                    val audioTrackIndex = arrayOf(-1)
                    val audioSampleCount = arrayOf(0L)

                    codec.setCallback(object : MediaCodec.Callback() {
                        override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
                            if (!recordingActive.get()) {
                                mc.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                try { session.record.stop() } catch (e: Exception) {}
                                return
                            }
                            val inBuf = mc.getInputBuffer(index)!!
                            // 恢复阻塞读取，利用麦克风硬件时钟作为天然同步源
                            val read = session.record.read(inBuf, Math.min(inBuf.capacity(), 4096))
                            if (read > 0) {
                                var pts = (audioSampleCount[0] * 1000000L) / session.sampleRate
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    val ts = android.media.AudioTimestamp()
                                    if (session.record.getTimestamp(ts, android.media.AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                                        val hwPts = (ts.nanoTime - ptsNormalizer.globalStartNs) / 1000L
                                        if (hwPts > 0 && Math.abs(hwPts - pts) < 50000) pts = hwPts
                                    }
                                }
                                audioSampleCount[0] = audioSampleCount[0] + read / (session.channelCount * 2)

                                // 仅主音频轨参与视频时间轴对齐（系统声音优先，其次麦克风）
                                if (isPrimary) {
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
                                val fallbackPts = (audioSampleCount[0] * 1000000L) / session.sampleRate
                                inBuf.clear()
                                inBuf.put(dummySilence)
                                audioSampleCount[0] = audioSampleCount[0] + dummySilence.size / (session.channelCount * 2)
                                mc.queueInputBuffer(index, 0, dummySilence.size, fallbackPts, 0)
                                if (read <= 0) {
                                    try { Thread.sleep(5) } catch (e: Exception) {}
                                }
                            }
                        }

                        override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                            val isAudioEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (info.size > 0 && audioTrackIndex[0] >= 0 && !isAudioEos) {
                                ptsNormalizer.normalizeTimeBase(info)
                                finalMuxer.writeSampleData(audioTrackIndex[0], mc.getOutputBuffer(index), info)
                            }
                            mc.releaseOutputBuffer(index, false)
                            if (isAudioEos) eosLatch.countDown()
                        }

                        override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
                            audioTrackIndex[0] = finalMuxer.addTrack(format)
                        }

                        override fun onError(mc: MediaCodec, e: MediaCodec.CodecException) {
                            RecorderLog.e(TAG, "Audio ($label) Codec Fatal Error: ${e.message}")
                            if (!e.isRecoverable && !e.isTransient) recordingActive.set(false)
                        }
                    }, Handler(audioCbThread.looper))

                    val audioFormat = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        session.sampleRate,
                        session.channelCount
                    ).apply {
                        setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                    }
                    codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }

                muxer.start()
                renderer = GlFrameRenderer(candidate.width, candidate.height)
                resourceManager.addRenderer(renderer)
                renderer.initialize(inputSurface)
                resourceManager.addSurface(inputSurface)
                renderer.updateSourceSize(displayController.screenWidth, displayController.screenHeight)
                displayController.bindCaptureSurface(shellContext, renderer.inputSurface, candidate.width, candidate.height)
                resourceManager.addCaptureSurface(displayController)

                videoCodec.start()
                for (pipeline in audioPipelines) {
                    pipeline.codec.start()
                    pipeline.session.record.startRecording()
                }

                if (audioPipelines.isNotEmpty() && android.os.Build.VERSION.SDK_INT == 30) {
                    try {
                        // 录音启动完毕，模拟按下返回键，退掉透明 Activity，让用户无缝留在当前应用中
                        Runtime.getRuntime().exec("input keyevent 4")
                    } catch (e: Exception) {}
                }

                val framePumper = FramePumper(displayController, shellContext, renderer, ptsNormalizer, bitrateController)
                framePumper.pump(recordingActive, candidate.fps, initialRotation, candidate.width, candidate.height)

                videoCodec.signalEndOfInputStream()
                for (pipeline in audioPipelines) {
                    try { pipeline.session.record.stop() } catch (e: Exception) {}
                }
                try { videoEosLatch.await(10, TimeUnit.SECONDS) } catch (e: InterruptedException) {}
                for (pipeline in audioPipelines) {
                    try { pipeline.eosLatch.await(5, TimeUnit.SECONDS) } catch (e: InterruptedException) {}
                }

                return true
            } catch (e: Exception) {
                RecorderLog.e(TAG, "Engine failed: ${e.javaClass.simpleName} - ${e.message}")
                recordingActive.set(false)
                return false
            }
        }
    }
}