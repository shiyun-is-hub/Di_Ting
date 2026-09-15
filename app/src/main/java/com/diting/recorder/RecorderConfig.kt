package com.diting.recorder

object RecorderConfig {
    const val OUTPUT_DIR = "/sdcard/Movies/Diting"
    const val PID_FILE_PATH = "/data/local/tmp/diting_recorder.pid"
    const val STOP_FLAG_PATH = "/data/local/tmp/diting_recorder.stop"
    const val DONE_FLAG_PATH = "/data/local/tmp/diting_recorder.done"
    const val REMOTE_APK_PATH = "/data/local/tmp/diting_recorder.apk"

    // 设置页默认值（同时作为手动 shell 启动模式的默认参数）
    const val QUALITY_SCREEN = "screen"
    const val QUALITY_1080P = "1080p"
    const val QUALITY_720P = "720p"
    const val QUALITY_360P = "360p"

    const val DEFAULT_QUALITY = QUALITY_1080P
    const val DEFAULT_FPS = 60

    // 音频与输出默认值
    const val DEFAULT_MIC_ENABLED = false
    const val DEFAULT_SYS_AUDIO_ENABLED = true

    const val DEFAULT_VIDEO_BITRATE_BPS = 8_000_000
    const val DOWNGRADE_VIDEO_BITRATE_BPS = 4_000_000
    const val DEFAULT_FALLBACK_FPS = 60
    const val DEFAULT_I_FRAME_INTERVAL = 1

    const val DEFAULT_AUDIO_BITRATE_BPS = 192_000
    const val DEFAULT_AUDIO_SAMPLE_RATE = 48000
    const val DEFAULT_AUDIO_MAX_INPUT_SIZE = 16_384

    const val FIRST_FRAME_TIMEOUT_MS = 1_500L
    const val MAX_STARTUP_REBINDS = 2
    const val RUNTIME_FRAME_TIMEOUT_MS = 2_000L
    const val MAX_RUNTIME_REBINDS = 3

    const val SEGMENT_DURATION_MS = 5_000L
    const val FLOAT_ITEM_SIZE_DP = 45f
}