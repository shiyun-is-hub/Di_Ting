package com.diting.recorder.system

import android.content.Context
import android.os.PowerManager
import com.diting.recorder.RecorderLog

object WakeLockController {
    private const val TAG = "WakeLockController"
    private var sWakeLock: PowerManager.WakeLock? = null

    @JvmStatic
    fun acquire(context: Context) {
        try {
            if (sWakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    sWakeLock = pm.newWakeLock(6 or 268435456, "Diting:Screen_Lock") // 强制屏幕常亮，防止系统自动息屏导致录像卡死
                    sWakeLock?.setReferenceCounted(false)   // ← 修改：使用 setter 方法
                }
            }
            if (sWakeLock != null && !sWakeLock!!.isHeld) {
                sWakeLock!!.acquire()
                RecorderLog.i("ZR.Core", "Partial WakeLock acquired (CPU forced awake).")
            }
        } catch (e: Exception) {
            RecorderLog.w("ZR.Core", "Failed to acquire WakeLock: " + e.message)
        }
    }

    @JvmStatic
    fun release() {
        try {
            if (sWakeLock != null && sWakeLock!!.isHeld) {
                sWakeLock!!.release()
                RecorderLog.i("ZR.Core", "Partial WakeLock released.")
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }
}
