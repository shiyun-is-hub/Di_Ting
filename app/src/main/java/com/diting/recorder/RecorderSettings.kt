package com.diting.recorder

import android.content.Context

/**
 * 通用设置存储（SharedPreferences 封装）。
 * 目前承载：录制画质、录制帧率、光暗模式。
 * 后续的权限 / 麦克风 / 系统声音 / 保存位置等配置将在此扩展。
 */
object RecorderSettings {
    private const val PREFS_NAME = "diting_recorder_settings"

    const val KEY_QUALITY = "quality"
    const val KEY_FPS = "fps"
    const val KEY_THEME = "theme_mode"

    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val KEY_AUDIO_CHECK_MODE = "audio_check_mode"
    const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"

    // 音频自检：自动（每次打开应用检测）/ 手动（用户点击检测）/ 关（不检测）
    const val AUDIO_CHECK_AUTO = "auto"
    const val AUDIO_CHECK_MANUAL = "manual"
    const val AUDIO_CHECK_OFF = "off"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getQuality(context: Context): String =
        prefs(context).getString(KEY_QUALITY, RecorderConfig.DEFAULT_QUALITY)
            ?: RecorderConfig.DEFAULT_QUALITY

    fun setQuality(context: Context, value: String) {
        prefs(context).edit().putString(KEY_QUALITY, value).apply()
    }

    fun getFps(context: Context): Int =
        prefs(context).getInt(KEY_FPS, RecorderConfig.DEFAULT_FPS)

    fun setFps(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_FPS, value).apply()
    }

    fun getThemeMode(context: Context): String =
        prefs(context).getString(KEY_THEME, THEME_AUTO) ?: THEME_AUTO

    fun setThemeMode(context: Context, value: String) {
        prefs(context).edit().putString(KEY_THEME, value).apply()
    }

    fun getAudioCheckMode(context: Context): String =
        prefs(context).getString(KEY_AUDIO_CHECK_MODE, AUDIO_CHECK_AUTO) ?: AUDIO_CHECK_AUTO

    fun setAudioCheckMode(context: Context, value: String) {
        prefs(context).edit().putString(KEY_AUDIO_CHECK_MODE, value).apply()
    }

    fun isFirstLaunchDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FIRST_LAUNCH_DONE, false)

    fun setFirstLaunchDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_FIRST_LAUNCH_DONE, true).apply()
    }

    //
    const val KEY_MIC_ENABLED = "mic_enabled"
    const val KEY_SYS_AUDIO_ENABLED = "sys_audio_enabled"
    const val KEY_OUTPUT_DIR = "output_dir"
    const val KEY_PRIVACY_AGREED = "privacy_agreed"

    fun isMicEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MIC_ENABLED, RecorderConfig.DEFAULT_MIC_ENABLED)

    fun setMicEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIC_ENABLED, value).apply()
    }

    fun isSysAudioEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYS_AUDIO_ENABLED, RecorderConfig.DEFAULT_SYS_AUDIO_ENABLED)

    fun setSysAudioEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYS_AUDIO_ENABLED, value).apply()
    }

    fun getOutputDir(context: Context): String =
        prefs(context).getString(KEY_OUTPUT_DIR, RecorderConfig.OUTPUT_DIR) ?: RecorderConfig.OUTPUT_DIR

    fun setOutputDir(context: Context, value: String) {
        prefs(context).edit().putString(KEY_OUTPUT_DIR, value).apply()
    }

    //
    fun isPrivacyAgreed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRIVACY_AGREED, false)

    fun setPrivacyAgreed(context: Context) {
        prefs(context).edit().putBoolean(KEY_PRIVACY_AGREED, true).apply()
    }
}
