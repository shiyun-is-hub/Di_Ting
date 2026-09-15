package com.diting.recorder

/**
 * 由设置页拼入启动命令，录制进程在 main() 中解析。
 * 手动 shell 启动示例：
 *   --quality=1080p --fps=60 --mic=on --sysaudio=on --out=/sdcard/Movies/Diting
 */
data class RecorderOptions(
    val quality: String,
    val fps: Int,
    val micEnabled: Boolean,
    val sysAudioEnabled: Boolean,
    val outputDir: String
) {
    companion object {
        val VALID_QUALITIES = setOf(
            RecorderConfig.QUALITY_SCREEN,
            RecorderConfig.QUALITY_1080P,
            RecorderConfig.QUALITY_720P,
            RecorderConfig.QUALITY_360P
        )
        val VALID_FPS = setOf(30, 40, 50, 60)

        fun parse(args: Array<String>): RecorderOptions {
            var quality = RecorderConfig.DEFAULT_QUALITY
            var fps = RecorderConfig.DEFAULT_FPS
            var micEnabled = RecorderConfig.DEFAULT_MIC_ENABLED
            var sysAudioEnabled = RecorderConfig.DEFAULT_SYS_AUDIO_ENABLED
            var outputDir = RecorderConfig.OUTPUT_DIR

            for (arg in args) {
                when {
                    arg.startsWith("--quality=") -> {
                        val value = arg.substringAfter("--quality=")
                        if (VALID_QUALITIES.contains(value)) quality = value
                    }
                    arg.startsWith("--fps=") -> {
                        val value = arg.substringAfter("--fps=").toIntOrNull()
                        if (value != null && VALID_FPS.contains(value)) fps = value
                    }
                    arg.startsWith("--mic=") -> {
                        micEnabled = arg.substringAfter("--mic=").trim().equals("on", ignoreCase = true)
                    }
                    arg.startsWith("--sysaudio=") -> {
                        sysAudioEnabled = arg.substringAfter("--sysaudio=").trim().equals("on", ignoreCase = true)
                    }
                    arg.startsWith("--out=") -> {
                        val value = arg.substringAfter("--out=").trim()
                        if (value.isNotBlank()) outputDir = value
                    }
                }
            }
            return RecorderOptions(quality, fps, micEnabled, sysAudioEnabled, outputDir)
        }
    }
}