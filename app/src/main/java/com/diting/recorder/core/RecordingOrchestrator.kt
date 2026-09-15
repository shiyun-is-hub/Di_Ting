package com.diting.recorder.core

import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.diting.recorder.RecorderLog
import com.diting.recorder.RecorderOptions
import com.diting.recorder.audio.AudioCaptureFactory
import com.diting.recorder.capture.DisplayCaptureController
import com.diting.recorder.system.NativeCore
import com.diting.recorder.system.ShellEnvironment
import com.diting.recorder.system.WakeLockController
import com.diting.recorder.ui.FloatingMenuController
import com.diting.recorder.ui.RawTouchDispatcher
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

object RecordingOrchestrator {
    private const val TAG = "ZR.Orchestrator"
    private val recordingActive = AtomicBoolean(false)
    private var currentRecordThread: Thread? = null

    @JvmStatic
    fun main(args: Array<String>) {
        // 快速诊断一次后直接退出
        if (args.contains("--audio-check")) {
            runAudioSelfCheck(args)
            return
        }
        if (args.contains("--audio-test")) {
            runAudioTest(args)
            return
        }
        val options = RecorderOptions.parse(args)
        setupProcessEnvironment(options.outputDir)

        // 解析由设置页传入的启动参数（手动 shell 启动时使用默认值）
        RecorderLog.i(TAG, "Options: quality=${options.quality}, fps=${options.fps}, mic=${options.micEnabled}, sys=${options.sysAudioEnabled}, out=${options.outputDir}")

        val shellContext = ShellEnvironment.getShellContext()
        val displayController = DisplayCaptureController(ShellEnvironment.getDisplayManagerGlobal())
        displayController.refreshDisplayInfo()

        val menuController = FloatingMenuController()
        var menuPos = mutableListOf(100, 100)
        menuController.init(menuPos[0], menuPos[1])
        menuController.drawMenu(false)

        val touchDispatcher = setupTouchDispatcher(menuController, menuPos, shellContext, displayController, options)
        touchDispatcher.start(
            displayController.screenWidth,
            displayController.screenHeight,
            { displayController.rotation }
        )

        displayController.registerDisplayListener(Handler(Looper.getMainLooper()))
        Looper.loop()
    }

    /** 音频自检模式 快速诊断麦克风 / 系统声音采集能力，结果写入 --check-out 指定的文件后退出。 */
    private fun runAudioSelfCheck(args: Array<String>) {
        val outPath = args.firstOrNull { it.startsWith("--check-out=") }?.substringAfter("--check-out=")
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        try {
            ShellEnvironment.bypassHiddenApi()
            ShellEnvironment.applyWorkarounds()
        } catch (t: Throwable) {
            RecorderLog.w(TAG, "Audio check env setup failed: " + rootCause(t))
        }

        val micResult = checkAudioSource { AudioCaptureFactory.createMicSession() }
        val sysResult = checkAudioSource { AudioCaptureFactory.createSystemAudioSession() }

        val report = "mic=$micResult\nsys=$sysResult\ntime=${System.currentTimeMillis()}\n"
        if (outPath != null) {
            try {
                val f = File(outPath)
                f.parentFile?.mkdirs()
                f.writeText(report)
                RecorderLog.i(TAG, "Audio check report written: $outPath")
            } catch (t: Throwable) {
                RecorderLog.e(TAG, "Audio check write failed", t)
            }
        }
        System.exit(0)
    }

    /** 对单个音频源做约 1.5 秒试采集，返回 ok / warn / fail 结果串。 */
    private fun checkAudioSource(create: () -> AudioCaptureFactory.AudioCaptureSession): String {
        return try {
            val session = create()
            try {
                session.record.startRecording()
                val buffer = ByteArray(4096)
                var total = 0L
                val deadline = System.nanoTime() + 1_500_000_000L
                while (System.nanoTime() < deadline) {
                    val read = session.record.read(buffer, 0, buffer.size, android.media.AudioRecord.READ_NON_BLOCKING)
                    if (read > 0) total += read
                    try {
                        Thread.sleep(20)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                try { session.record.stop() } catch (e: Exception) {
                }
                if (total > 0) "ok:$total" else "warn:0"
            } finally {
                try { session.release() } catch (e: Exception) {
                }
            }
        } catch (t: Throwable) {
            "fail:" + rootCause(t)
        }
    }

    /** 交互式音频测试 持续捕获麦克风 / 系统声音并实时写入电平文件（供音频测试页显示）。 */
    private fun runAudioTest(args: Array<String>) {
        val levelsPath = args.firstOrNull { it.startsWith("--levels=") }?.substringAfter("--levels=")
        val stopPath = args.firstOrNull { it.startsWith("--stop-flag=") }?.substringAfter("--stop-flag=")
        val outPath = args.firstOrNull { it.startsWith("--check-out=") }?.substringAfter("--check-out=")

        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        try {
            ShellEnvironment.bypassHiddenApi()
            ShellEnvironment.applyWorkarounds()
        } catch (t: Throwable) {
            RecorderLog.w(TAG, "Audio test env setup failed: " + rootCause(t))
        }

        var micSession: AudioCaptureFactory.AudioCaptureSession? = null
        var sysSession: AudioCaptureFactory.AudioCaptureSession? = null
        var micState = "starting"
        var sysState = "starting"
        var micBytes = 0L
        var sysBytes = 0L
        try {
            micSession = AudioCaptureFactory.createMicSession()
            micState = "ok"
        } catch (t: Throwable) {
            micState = "fail:" + rootCause(t)
        }
        try {
            sysSession = AudioCaptureFactory.createSystemAudioSession()
            sysState = "ok"
        } catch (t: Throwable) {
            sysState = "fail:" + rootCause(t)
        }

        if (micSession == null && sysSession == null) {
            val report = "mic=$micState\nsys=$sysState\ntime=${System.currentTimeMillis()}\n"
            if (outPath != null) {
                try {
                    val f = File(outPath)
                    f.parentFile?.mkdirs()
                    f.writeText(report)
                } catch (t: Throwable) {
                }
            }
            writeLevels(levelsPath, 0.0, 0.0, micState, sysState, 0L, 0L, 0L, true)
            System.exit(0)
        }

        try { micSession?.record?.startRecording() } catch (t: Throwable) { micState = "fail:" + rootCause(t) }
        try { sysSession?.record?.startRecording() } catch (t: Throwable) { sysState = "fail:" + rootCause(t) }

        val buffer = ByteArray(8192)
        var micWinSq = 0.0
        var micWinN = 0L
        var sysWinSq = 0.0
        var sysWinN = 0L
        val startMs = android.os.SystemClock.elapsedRealtime()
        var lastWriteMs = 0L
        while (true) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - startMs >= 60_000L) break
            if (stopPath != null && File(stopPath).exists()) break
            val micDrain = micSession?.let { drainRecord(it.record, buffer) }
            if (micDrain != null) {
                micBytes += micDrain.second * 2
                micWinSq += micDrain.first
                micWinN += micDrain.second
            }
            val sysDrain = sysSession?.let { drainRecord(it.record, buffer) }
            if (sysDrain != null) {
                sysBytes += sysDrain.second * 2
                sysWinSq += sysDrain.first
                sysWinN += sysDrain.second
            }
            if (now - lastWriteMs >= 100L) {
                lastWriteMs = now
                val micRms = if (micWinN > 0) Math.sqrt(micWinSq / micWinN) / 32768.0 else 0.0
                val sysRms = if (sysWinN > 0) Math.sqrt(sysWinSq / sysWinN) / 32768.0 else 0.0
                writeLevels(levelsPath, micRms, sysRms, micState, sysState, micBytes, sysBytes, now - startMs, false)
                micWinSq = 0.0; micWinN = 0L; sysWinSq = 0.0; sysWinN = 0L
            }
            try { Thread.sleep(20) } catch (e: InterruptedException) { break }
        }

        try { micSession?.record?.stop() } catch (t: Throwable) {}
        try { sysSession?.record?.stop() } catch (t: Throwable) {}
        try { micSession?.release() } catch (t: Throwable) {}
        try { sysSession?.release() } catch (t: Throwable) {}

        val micFinal = if (micState.startsWith("fail")) micState else if (micBytes > 0) "ok:$micBytes" else "warn:0"
        val sysFinal = if (sysState.startsWith("fail")) sysState else if (sysBytes > 0) "ok:$sysBytes" else "warn:0"
        val report = "mic=$micFinal\nsys=$sysFinal\ntime=${System.currentTimeMillis()}\n"
        if (outPath != null) {
            try {
                val f = File(outPath)
                f.parentFile?.mkdirs()
                f.writeText(report)
            } catch (t: Throwable) {
            }
        }
        writeLevels(levelsPath, 0.0, 0.0, micState, sysState, micBytes, sysBytes, android.os.SystemClock.elapsedRealtime() - startMs, true)
        if (stopPath != null) {
            try { File(stopPath).delete() } catch (t: Throwable) {}
        }
        System.exit(0)
    }

    /** 从 AudioRecord 非阻塞排空数据，返回（平方和, 样本数）。 */
    private fun drainRecord(record: android.media.AudioRecord, buffer: ByteArray): Pair<Double, Long> {
        var sq = 0.0
        var cnt = 0L
        while (true) {
            val n = record.read(buffer, 0, buffer.size, android.media.AudioRecord.READ_NON_BLOCKING)
            if (n <= 0) break
            var i = 0
            while (i + 1 < n) {
                val lo = buffer[i].toInt() and 255
                val hi = buffer[i + 1].toInt() and 255
                val s = lo or (hi shl 8)
                val v = if (s > 32767) s - 65536 else s
                sq += (v * v).toDouble()
                cnt++
                i += 2
            }
        }
        return Pair(sq, cnt)
    }

    /** 写入实时电平文件（供音频测试页轮询）。 */
    private fun writeLevels(path: String?, micRms: Double, sysRms: Double, micState: String, sysState: String, micBytes: Long, sysBytes: Long, elapsedMs: Long, done: Boolean) {
        if (path == null) return
        try {
            val sb = StringBuilder()
            sb.append("mic=").append(String.format(java.util.Locale.US, "%.4f", micRms)).append('\n')
            sb.append("sys=").append(String.format(java.util.Locale.US, "%.4f", sysRms)).append('\n')
            sb.append("micState=").append(micState).append('\n')
            sb.append("sysState=").append(sysState).append('\n')
            sb.append("micBytes=").append(micBytes).append('\n')
            sb.append("sysBytes=").append(sysBytes).append('\n')
            sb.append("elapsed=").append(elapsedMs).append('\n')
            sb.append("done=").append(if (done) 1 else 0).append('\n')
            File(path).writeText(sb.toString())
        } catch (t: Throwable) {
        }
    }

    /** 提取异常根因（含消息），便于精简展示。 */
    private fun rootCause(t: Throwable): String {
        var cur = t
        var depth = 0
        while (cur.cause != null && cur !== cur.cause && depth < 6) {
            cur = cur.cause!!
            depth++
        }
        val msg = (cur.message ?: "").replace('\n', ' ').trim().take(120)
        return cur.javaClass.simpleName + (if (msg.isEmpty()) "" else ": " + msg)
    }

    private fun setupProcessEnvironment(outputDir: String) {
        val pidFile = File("/data/local/tmp/diting_recorder.pid")
        if (pidFile.exists()) {
            try {
                val oldPid = String(Files.readAllBytes(pidFile.toPath())).trim()
                if (File("/proc/$oldPid").exists()) {
                    RecorderLog.e(TAG, "Another instance is already running (PID $oldPid). Exiting.")
                    System.exit(1)
                }
            } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            pidFile.delete()
        }
        try {
            Files.write(pidFile.toPath(), Process.myPid().toString().toByteArray())
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            RecorderLog.e(TAG, "Process crashed", e)
            recordingActive.set(false)
            try { Thread.sleep(1000) /* FIXME: 阻塞式休眠，建议重构为协程 delay() 或使用 ReentrantLock.Condition 同步机制 */ } catch (e: InterruptedException) {}
            System.exit(1)
        }

        if (Looper.getMainLooper() == null) Looper.prepareMainLooper()
        Process.setThreadPriority(Process.THREAD_PRIORITY_VIDEO)
        Binder.clearCallingIdentity()

        if (NativeCore.isAvailable()) {
            NativeCore.protectFromOOM()
            NativeCore.bindToPerformanceCores()
            NativeCore.lockMemoryIntoRAM()
        }

        ShellEnvironment.bypassHiddenApi()
        ShellEnvironment.applyWorkarounds()
        OutputManager.cleanupResidualEnvironment(outputDir)

        startStopMonitor()
    }

    private fun startStopMonitor() {
        Thread({
            val stopFlag = File("/data/local/tmp/diting_recorder.stop")
            val doneFlag = File("/data/local/tmp/diting_recorder.done")
            if (stopFlag.exists()) stopFlag.delete()
            if (doneFlag.exists()) doneFlag.delete()
            while (true) {
                if (stopFlag.exists()) {
                    RecorderLog.i(TAG, "Stop signal received, waiting for recording to finish...")
                    recordingActive.set(false)
                    stopFlag.delete()
                    currentRecordThread?.let {
                        try { it.join() } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
                    }
                    try { doneFlag.createNewFile() } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
                    System.exit(0)
                }
                try { Thread.sleep(200) /* FIXME: 阻塞式休眠，建议重构为协程 delay() 或使用 ReentrantLock.Condition 同步机制 */ } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            }
        }, "StopMonitorThread").start()
    }

    private fun setupTouchDispatcher(
        menuController: FloatingMenuController,
        menuPos: MutableList<Int>,
        shellContext: Context,
        displayController: DisplayCaptureController,
        options: RecorderOptions
    ): RawTouchDispatcher {
        return RawTouchDispatcher(object : RawTouchDispatcher.TouchListener {
            private var isDragging = false
            private var downX = 0f
            private var downY = 0f
            private var initialMenuX = 0
            private var initialMenuY = 0
            private var isDownInside = false

            override fun onDown(x: Float, y: Float) {
                val currentWidth = menuController.getCurrentWidth(recordingActive.get())
                if (x >= menuPos[0] && x <= menuPos[0] + currentWidth &&
                    y >= menuPos[1] && y <= menuPos[1] + menuController.getCurrentHeight()) {
                    isDownInside = true
                    isDragging = false
                    downX = x
                    downY = y
                    initialMenuX = menuPos[0]
                    initialMenuY = menuPos[1]
                } else {
                    isDownInside = false
                }
            }

            override fun onMove(x: Float, y: Float) {
                if (!isDownInside) return
                val dx = x - downX
                val dy = y - downY
                if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) isDragging = true
                if (isDragging) {
                    menuPos[0] = Math.max(0, Math.min(initialMenuX + dx.toInt(), displayController.screenWidth - menuController.getCurrentWidth(recordingActive.get())))
                    menuPos[1] = Math.max(0, Math.min(initialMenuY + dy.toInt(), displayController.screenHeight - menuController.getCurrentHeight()))
                    menuController.setPosition(menuPos[0], menuPos[1])
                }
            }

            override fun onUp() {
                if (!isDownInside) return
                if (!isDragging) {
                    val clickedIndex = ((downX - menuPos[0]) / FloatingMenuController.ITEM_SIZE).toInt()
                    try {
                        val v = shellContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        v?.vibrate(android.os.VibrationEffect.createOneShot(30, 100))
                    } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
                    if (!recordingActive.get()) {
                        when (clickedIndex) {
                            0 -> {
                                recordingActive.set(true)
                                menuController.drawMenu(true)
                                if (currentRecordThread == null || !currentRecordThread!!.isAlive) {
                                    currentRecordThread = Thread({
                                        try {
                                            WakeLockController.acquire(shellContext)
                                            val engine = RecordingEngine(shellContext, displayController, recordingActive, options)
                                            engine.start(displayController.rotation)
                                        } catch (t: Throwable) {
                                            RecorderLog.e(TAG, "Engine crashed", t)
                                        } finally {
                                            recordingActive.set(false)
                                            menuController.drawMenu(false)
                                            WakeLockController.release()
                                        }
                                    }, "RecordThread").also { it.start() }
                                }
                            }
                            2 -> {
                                recordingActive.set(false)
                                menuController.release()
                                Thread {
                                    currentRecordThread?.let { try { it.join() } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        } }
                                    WakeLockController.release()
                                    System.exit(0)
                                }.start()
                            }
                        }
                    } else if (clickedIndex == 0) {
                        recordingActive.set(false)
                        val restoredWidth = menuController.getCurrentWidth(false)
                        if (menuPos[0] + restoredWidth > displayController.screenWidth) {
                            menuPos[0] = Math.max(0, displayController.screenWidth - restoredWidth)
                            menuController.setPosition(menuPos[0], menuPos[1])
                        }
                        menuController.drawMenu(false)
                    }
                }
                isDownInside = false
                isDragging = false
            }
        })
    }
}