package com.diting.recorder.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 录屏的「停止」按钮挂在常驻通知上，因此通知权限是**功能必需**的：
 * 没有通知权限 → 用户无法从通知栏停止录屏（只能回应用内），体验会断掉。
 *
 * - Android 13 (API 33) 起，POST_NOTIFICATIONS 是运行时权限，需动态申请
 * - 若用户拒绝过（shouldShowRequestPermissionRationale=false），只能引导去系统设置手动开启
 */
object NotificationPermission {

    /** 是否已授予通知权限（API 33 以下默认视为已授予） */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** 是否需要（在 API 33+ 且未授权时）申请通知权限 */
    fun isNeeded(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(context)

    /**
     * 通知渠道是否被用户关闭（授权了权限但把渠道关了，通知同样出不来）。
     * 返回 true 表示渠道被关闭。
     */
    fun isChannelDisabled(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = nm.getNotificationChannel(channelId) ?: return false
            ch.importance == NotificationManager.IMPORTANCE_NONE
        } catch (t: Throwable) {
            false
        }
    }

    /** 跳转到本应用的「通知设置」页面（用户在权限弹窗拒绝后，只能到这里手动开启） */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            // 兜底：应用详情页
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t2: Throwable) {
            }
        }
    }

    /** 状态文案（用于设置页显示） */
    fun statusText(context: Context): String = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "系统版本无需申请（自动授权）✅"
        isGranted(context) -> "已授权 ✅"
        else -> "未授权 ⚠️"
    }
}