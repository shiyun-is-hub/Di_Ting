package com.diting.recorder.basic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.diting.recorder.MainActivity
import com.diting.recorder.R
import com.diting.recorder.RecorderConfig
import com.diting.recorder.RecorderOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 前台服务：持有 MediaProjection 并运行基础录制引擎；
 * 通知栏提供「停止」操作。
 */
class BasicRecordService : Service() {

    companion object {
        const val ACTION_START = "com.diting.recorder.action.BASIC_START"
        const val ACTION_STOP = "com.diting.recorder.action.BASIC_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_MIC = "mic"
        const val EXTRA_SYS_AUDIO = "sys_audio"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_FPS = "fps"
        const val EXTRA_OUT_DIR = "out_dir"
        const val EXTRA_BASE_NAME = "base_name"
        private const val CHANNEL_ID = "basic_recording"
        private const val NOTIFICATION_ID = 4101

        @Volatile
        var isRunning = false
            private set
    }

    private val recordingActive = AtomicBoolean(false)
    private var engineThread: Thread? = null
    private var projection: MediaProjection? = null
    private var resultCode = 0
    private var resultData: Intent? = null
    private var baseName = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        //
        // Android 要求 startForegroundService() 后必须在约 5 秒内调用 startForeground()。
        // 这里在 onCreate 中第一时间先用“通用类型”占位启动前台服务，确保不超时；
        // 待 onStartCommand 中拿到 MediaProjection 后，再用 mediaProjection 类型重新 startForeground 升级。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("正在准备录屏…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("正在准备录屏…"))
            }
        } catch (t: Throwable) {
            // 某些系统在未持有 MediaProjection 时不允许 mediaProjection 类型，
            // 回退为不带类型的前台服务，保证 5 秒时限不被打破。
            try {
                startForeground(NOTIFICATION_ID, buildNotification("正在准备录屏…"))
            } catch (t2: Throwable) {
                // 最后兜底：至少把通知发出去，避免异常抛出导致崩溃
                try { updateNotification("正在准备录屏…") } catch (_: Throwable) {}
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handleStopRequest()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (!isRunning) startBasic(intent)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startBasic(intent: Intent) {
        resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            stopSelf()
            return
        }
        baseName = intent.getStringExtra(EXTRA_BASE_NAME) ?: "Rec_basic"

        val options = RecorderOptions(
            quality = intent.getStringExtra(EXTRA_QUALITY) ?: RecorderConfig.DEFAULT_QUALITY,
            fps = intent.getIntExtra(EXTRA_FPS, RecorderConfig.DEFAULT_FPS),
            micEnabled = intent.getBooleanExtra(EXTRA_MIC, false),
            sysAudioEnabled = intent.getBooleanExtra(EXTRA_SYS_AUDIO, true),
            outputDir = intent.getStringExtra(EXTRA_OUT_DIR) ?: RecorderConfig.OUTPUT_DIR
        )

        // targetSdk 35 起：必须“先创建 MediaProjection，再启动
        // mediaProjection 类型的前台服务”，否则会抛 SecurityException。
        // 因此这里调整顺序：先获取 MediaProjection，再 startForeground。
        val mgr = getSystemService(MediaProjectionManager::class.java)
        val mp = try {
            mgr.getMediaProjection(resultCode, resultData!!)
        } catch (t: Throwable) {
            null
        }
        if (mp == null) {
            Toast.makeText(applicationContext, "录屏授权失败", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        projection = mp

        // 2) 已在 onCreate 中提前 startForeground 这里只做“类型升级”：
        //    拿到 MediaProjection 后，用 mediaProjection(+microphone) 类型重新声明前台服务。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && options.micEnabled) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, buildNotification("正在准备录屏…"), type)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("正在准备录屏…"))
            }
        } catch (t: Throwable) {
            // 升级失败不致命：onCreate 中已保证前台服务存活，录制流程继续。
        }

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                recordingActive.set(false)
            }
        }, Handler(Looper.getMainLooper()))

        isRunning = true
        recordingActive.set(true)
        engineThread = Thread({
            val engine = BasicRecordingEngine(applicationContext, mp, options, recordingActive, baseName) { success, msg ->
                onEngineFinished(success, msg)
            }
            engine.start()
        }, "BasicRecordThread").also { it.start() }
        updateNotification("正在录屏…")
    }

    private fun handleStopRequest() {
        if (isRunning) {
            recordingActive.set(false)
            updateNotification("正在保存…")
        } else {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun onEngineFinished(success: Boolean, message: String) {
        isRunning = false
        try { projection?.stop() } catch (t: Throwable) {}
        projection = null
        val text = if (success) "已保存到相册" else "录屏失败：$message"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
            updateNotification(text)
            Handler(Looper.getMainLooper()).postDelayed({
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }, 2500)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "录屏控制", NotificationManager.IMPORTANCE_LOW)
            ch.description = "基础录屏的常驻通知"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, BasicRecordService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(this, 2, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tb_player_record)
            .setContentTitle("谛听录屏")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPi)
            .addAction(R.drawable.ic_tb_player_stop, "停止", stopPi)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (t: Throwable) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            recordingActive.set(false)
        }
        isRunning = false
    }
}