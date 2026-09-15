package com.diting.recorder.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import com.diting.recorder.system.ShellEnvironment
import com.diting.recorder.RecorderLog
import com.diting.recorder.RecorderConfig

object AudioCaptureFactory {
    private const val TAG = "ZR.Audio"
    private const val AUDIO_SOURCE_REMOTE_SUBMIX = 8

    @JvmStatic
    @Throws(Exception::class)
    fun createBestEffortSession(): AudioCaptureSession {
        val ctx = try { ShellEnvironment.getShellContext() } catch (e: Exception) {
            RecorderLog.e(TAG, "Failed to get ShellContext for audio", e)
            throw e
        }
        return createBestEffortSessionWithContext(ctx)
    }

    @JvmStatic
    private fun createBestEffortSessionWithContext(shellContext: Context): AudioCaptureSession {
        var lastError: Exception? = null
        val strategies: List<() -> AudioCaptureSession?> = listOf(
            // 解除 Android 13 限制，降低至 Android 10 (Q) 以支持 scrcpy 同款动态音频策略
            { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createPlaybackPolicySession(shellContext, RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO) else null },
            { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createPlaybackPolicySession(shellContext, RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO) else null },
            { createRemoteSubmixSession(shellContext, RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO) },
            { createSimpleRecordSession(RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO) }
        )

        for (strategy in strategies) {
            try {
                val session = strategy()
                if (session != null) return session
            } catch (e: Exception) {
                lastError = e
                try { Thread.sleep(10) /* FIXME: 阻塞式休眠，建议重构为协程 delay() 或使用 ReentrantLock.Condition 同步机制 */ } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            }
        }
        throw lastError ?: RuntimeException("All audio strategies failed")
    }

    /** 音频自检 仅尝试麦克风采集（立体声，失败回退单声道）。 */
    @JvmStatic
    fun createMicSession(): AudioCaptureSession {
        var lastError: Exception? = null
        for (mask in intArrayOf(AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO)) {
            try {
                return createSimpleRecordSession(RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, mask)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Mic capture unavailable")
    }

    /** 音频自检 仅尝试系统声音（播放捕获 → REMOTE_SUBMIX 回退）。 */
    @JvmStatic
    fun createSystemAudioSession(): AudioCaptureSession {
        var lastError: Exception? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val ctx = ShellEnvironment.getShellContext()
                return createPlaybackPolicySession(ctx, RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO)
            } catch (e: Exception) {
                lastError = e
            }
        }
        try {
            val ctx = ShellEnvironment.getShellContext()
            return createRemoteSubmixSession(ctx, RecorderConfig.DEFAULT_AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO)
        } catch (e: Exception) {
            lastError = e
        }
        throw lastError ?: RuntimeException("System audio capture unavailable")
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun createPlaybackPolicySession(context: Context, sampleRate: Int, channelMask: Int): AudioCaptureSession {
        val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setFlags(0x100).build()
        val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule" + "$" + "Builder")
        @Suppress("DEPRECATION")
        val ruleBuilder = ruleBuilderClass.newInstance()

        // 补齐 scrcpy 中的 TargetMixRole，解决录制时扬声器无声的问题
        try {
            val mixRolePlayers = Class.forName("android.media.audiopolicy.AudioMixingRule").getField("MIX_ROLE_PLAYERS").getInt(null)
            ruleBuilderClass.getMethod("setTargetMixRole", Int::class.java).invoke(ruleBuilder, mixRolePlayers)
            // 顺便加上允许捕获语音通信的声音
            ruleBuilderClass.getMethod("voiceCommunicationCaptureAllowed", Boolean::class.java).invoke(ruleBuilder, true)
        } catch (e: Exception) {}

        ruleBuilderClass.getMethod("addRule", AudioAttributes::class.java, Int::class.java).invoke(ruleBuilder, attr, 1)
        val rule = ruleBuilderClass.getMethod("build").invoke(ruleBuilder)

        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelMask).build()
        val mixClass = Class.forName("android.media.audiopolicy.AudioMix")
        val routeFlag = mixClass.getField("ROUTE_FLAG_LOOP_BACK_RENDER").getInt(null)
        val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix" + "$" + "Builder")
        val mixBuilder = mixBuilderClass.getConstructor(Class.forName("android.media.audiopolicy.AudioMixingRule")).newInstance(rule)
        mixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, format)
        mixBuilderClass.getMethod("setRouteFlags", Int::class.java).invoke(mixBuilder, routeFlag)
        val audioMix = mixBuilderClass.getMethod("build").invoke(mixBuilder)

        val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
        val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy" + "$" + "Builder")
        val policyBuilder = policyBuilderClass.getConstructor(Context::class.java).newInstance(context)
        policyBuilderClass.getMethod("addMix", mixClass).invoke(policyBuilder, audioMix)
        val audioPolicy = policyBuilderClass.getMethod("build").invoke(policyBuilder)

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val res = AudioManager::class.java.getMethod("registerAudioPolicy", policyClass).invoke(am, audioPolicy) as Int
        if (res != 0) throw RuntimeException("registerAudioPolicy failed: $res")

        val record = policyClass.getMethod("createAudioRecordSink", mixClass).invoke(audioPolicy, audioMix) as AudioRecord
        return AudioCaptureSession(am, audioPolicy, record, sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, "playback")
    }

    private fun createRemoteSubmixSession(context: Context, sampleRate: Int, channelMask: Int): AudioCaptureSession {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val builder = AudioRecord.Builder().setAudioSource(AUDIO_SOURCE_REMOTE_SUBMIX).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelMask).build()).setBufferSizeInBytes(Math.max(minBuf * 4, 4096))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setContext(context)
        return AudioCaptureSession(null, null, builder.build(), sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, "remotesubmix")
    }

    private fun createSimpleRecordSession(sampleRate: Int, channelMask: Int): AudioCaptureSession {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        // 兜底方案必须使用麦克风 (1 = MediaRecorder.AudioSource.MIC)，确保至少能录到外界声音
        return AudioCaptureSession(null, null, AudioRecord(1, sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, minBuf * 4), sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, "simple")
    }

    private fun unregisterPolicy(am: AudioManager, policy: Any) {
        try {
            AudioManager::class.java.getMethod("unregisterAudioPolicy", Class.forName("android.media.audiopolicy.AudioPolicy")).invoke(am, policy)
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }

    class AudioCaptureSession(
        private val audioManager: AudioManager?,
        private val audioPolicy: Any?,
        @JvmField val record: AudioRecord,
        @JvmField val sampleRate: Int,
        val channelMask: Int,
        val encoding: Int,
        val mode: String
    ) {
        init {
            // 拦截静默失败：如果底层返回了未初始化的实例，直接抛出异常让工厂去尝试下一个降级方案
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                try { record.release() } catch (e: Exception) {}
                throw IllegalStateException("AudioRecord is UNINITIALIZED in mode: $mode")
            }
        }
        @JvmField val channelCount = if (channelMask == AudioFormat.CHANNEL_IN_MONO) 1 else 2

        fun release() {
            try { record.release() } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            if (audioManager != null && audioPolicy != null) {
                unregisterPolicy(audioManager, audioPolicy)
            }
        }
    }
}