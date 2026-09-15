package com.diting.recorder

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.diting.recorder.crash.CrashHandler
import com.google.android.material.color.DynamicColors

/**
 * - 启动时按用户选择应用日/夜间模式（跟随系统 / 浅色 / 深色）
 * - 在 Android 12+ 上启用动态取色（跟随系统壁纸主题），使界面与系统原生风格一致
 */
class RecorderApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 全局未捕获异常处理（需尽早安装）
        CrashHandler.install(this)

        val nightMode = when (RecorderSettings.getThemeMode(this)) {
            RecorderSettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            RecorderSettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        try {
            DynamicColors.applyToActivitiesIfAvailable(this)
        } catch (t: Throwable) {
        }
    }
}