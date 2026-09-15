package com.diting.recorder.ui

import com.diting.recorder.RecorderLog
import java.io.*
import java.util.function.Supplier

class RawTouchDispatcher(private val listener: TouchListener) {

    companion object {
        private const val TAG = "RawTouchDispatcher"
        private const val MAX_SLOTS = 10
        private const val MOVE_EVENT_THROTTLE_MS = 16
    }

    interface TouchListener {
        fun onDown(x: Float, y: Float)
        fun onMove(x: Float, y: Float)
        fun onUp()
    }

    private var thread: Thread? = null
    @Volatile
    private var running = false
    private var touchMaxX = 1f
    private var touchMaxY = 1f
    private var currentEventStream: FileInputStream? = null

    private var currentSlot = 0
    private val slotX = IntArray(MAX_SLOTS) { -1 }
    private val slotY = IntArray(MAX_SLOTS) { -1 }
    private val slotTrackingIds = LongArray(MAX_SLOTS) { -1L }
    private val slotActive = BooleanArray(MAX_SLOTS)

    private var activeSlotIdx = -1
    private var activeTrackingId = -1L
    private var lastListenerX = -1f
    private var lastListenerY = -1f
    private var lastMoveEventTimeMs = 0L

    fun start(logicalWidth: Int, logicalHeight: Int, rotationSupplier: Supplier<Int>) {
        running = true
        val t = Thread {
            var fis: FileInputStream? = null
            try {
                val p = Runtime.getRuntime().exec("getevent -pl")
                val br = BufferedReader(InputStreamReader(p.inputStream))
var line: String?
                var currentDev = ""
                var currentDevName = ""
                var bestDev = ""

                while (br.readLine().also { line = it } != null) {
                    if (line!!.startsWith("add device")) {
                        currentDev = line!!.substring(line!!.lastIndexOf(' ') + 1).trim()
                    }
                    if (line!!.contains("name:")) {
                        currentDevName = line!!.substring(line!!.indexOf('"') + 1, line!!.lastIndexOf('"')).lowercase()
                    }

                    // 跳过已知的虚拟输入设备或传感器
                    if (currentDevName.contains("virtual") || currentDevName.contains("sensor") || currentDevName.contains("bma")) {
                        continue
                    }

                    // 优先锁定真实的 MT 多点触控屏
                    if (line!!.contains("ABS_MT_POSITION_X")) {
                        if (bestDev.isEmpty() || bestDev == "/dev/input/event1") {
                            bestDev = currentDev
                            touchMaxX = extractMax(line!!)
                        }
                    } else if (line!!.contains("ABS_X") && bestDev.isEmpty()) {
                        bestDev = currentDev
                        touchMaxX = extractMax(line!!)
                    }

                    if ((line!!.contains("ABS_MT_POSITION_Y") || line!!.contains("ABS_Y")) && currentDev == bestDev) {
                        touchMaxY = extractMax(line!!)
                    }
                }

                // 兜底降级
                if (bestDev.isEmpty()) bestDev = "/dev/input/event1"
                p.destroy()

                val is64BitKernel: Boolean = try {
                    val pArch = Runtime.getRuntime().exec("uname -m")
                    val brArch = BufferedReader(InputStreamReader(pArch.inputStream))
                    val arch = brArch.readLine()
                    pArch.destroy()
                    !(arch != null && (arch.contains("armv7") || arch.contains("i686")))
                } catch (e: Exception) {
                    android.os.Process.is64Bit()
                }

                val eventSize = if (is64BitKernel) 24 else 16
                val typeOffset = if (is64BitKernel) 16 else 8
                val codeOffset = if (is64BitKernel) 18 else 10
                val valueOffset = if (is64BitKernel) 20 else 12

                fis = FileInputStream(bestDev)
                currentEventStream = fis
                val dis = DataInputStream(BufferedInputStream(fis, 8192))
                val eventBuffer = ByteArray(eventSize)

                while (running) {
                    try {
                        dis.readFully(eventBuffer)
                    } catch (e: EOFException) {
                        break
                    }

                    val type = (eventBuffer[typeOffset].toInt() and 0xFF) or ((eventBuffer[typeOffset + 1].toInt() and 0xFF) shl 8)
                    val code = (eventBuffer[codeOffset].toInt() and 0xFF) or ((eventBuffer[codeOffset + 1].toInt() and 0xFF) shl 8)
                    var value = (eventBuffer[valueOffset].toInt() and 0xFF).toLong() or
                                ((eventBuffer[valueOffset + 1].toInt() and 0xFF) shl 8).toLong() or
                                ((eventBuffer[valueOffset + 2].toInt() and 0xFF) shl 16).toLong() or
                                ((eventBuffer[valueOffset + 3].toInt() and 0xFF) shl 24).toLong()

                    if (eventBuffer[valueOffset + 3].toInt() and 0x80 != 0) {
                        value = value or (-1L shl 32) // 符号扩展
                    }

                    if (type == 3) {
                        when (code) {
                            47, 0x002f -> {
                                if (value >= 0 && value < MAX_SLOTS) {
                                    currentSlot = value.toInt()
                                }
                            }
                            53, 0 -> slotX[currentSlot] = value.toInt()
                            54, 1 -> slotY[currentSlot] = value.toInt()
                            57 -> slotTrackingIds[currentSlot] = value
                        }
                    } else if (type == 0 && code == 0) {
                        for (i in 0 until MAX_SLOTS) {
                            // 安全判断：trackingId == -1L 表示槽位无效
                            slotActive[i] = slotTrackingIds[i] != -1L
                        }

                        if (activeSlotIdx == -1) {
                            for (i in 0 until MAX_SLOTS) {
                                if (slotActive[i] && slotX[i] != -1 && slotY[i] != -1) {
                                    activeSlotIdx = i
                                    activeTrackingId = slotTrackingIds[i]

                                    val finalX = calculateRotatedX(slotX[i], slotY[i], logicalWidth, logicalHeight, rotationSupplier)
                                    val finalY = calculateRotatedY(slotX[i], slotY[i], logicalWidth, logicalHeight, rotationSupplier)

                                    listener.onDown(finalX, finalY)
                                    lastListenerX = finalX
                                    lastListenerY = finalY
                                    break
                                }
                            }
                        } else {
                            if (!slotActive[activeSlotIdx] || slotTrackingIds[activeSlotIdx] != activeTrackingId) {
                                listener.onUp()
                                activeSlotIdx = -1
                                activeTrackingId = -1
                            } else {
                                val finalX = calculateRotatedX(slotX[activeSlotIdx], slotY[activeSlotIdx], logicalWidth, logicalHeight, rotationSupplier)
                                val finalY = calculateRotatedY(slotX[activeSlotIdx], slotY[activeSlotIdx], logicalWidth, logicalHeight, rotationSupplier)

                                if (finalX != lastListenerX || finalY != lastListenerY) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastMoveEventTimeMs >= MOVE_EVENT_THROTTLE_MS) {
                                        listener.onMove(finalX, finalY)
                                        lastMoveEventTimeMs = now
                                    }
                                    lastListenerX = finalX
                                    lastListenerY = finalY
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught", e)
            } finally {
                try {
                    fis?.close()
                } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            }
        }
        t.setDaemon(true)
        t.start()
        thread = t
    }

    private fun calculateRotatedX(x: Int, y: Int, logicalWidth: Int, logicalHeight: Int, rotationSupplier: Supplier<Int>): Float {
        val s = Math.min(logicalWidth, logicalHeight).toFloat()
        val l = Math.max(logicalWidth, logicalHeight).toFloat()
        val rawLx = (x / touchMaxX) * s
        val rawLy = (y / touchMaxY) * l
        return when (rotationSupplier.get()) {
            1 -> rawLy
            2 -> s - rawLx
            3 -> l - rawLy
            else -> rawLx
        }
    }

    private fun calculateRotatedY(x: Int, y: Int, logicalWidth: Int, logicalHeight: Int, rotationSupplier: Supplier<Int>): Float {
        val s = Math.min(logicalWidth, logicalHeight).toFloat()
        val l = Math.max(logicalWidth, logicalHeight).toFloat()
        val rawLx = (x / touchMaxX) * s
        val rawLy = (y / touchMaxY) * l
        return when (rotationSupplier.get()) {
            1 -> s - rawLx
            2 -> l - rawLy
            3 -> rawLx
            else -> rawLy
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        try {
            currentEventStream?.close()
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }

    private fun extractMax(line: String): Float {
        val idx = line.indexOf("max ")
        if (idx != -1) {
            val start = idx + 4
            val end = line.indexOf(',', start)
            val sub = if (end == -1) line.substring(start).trim() else line.substring(start, end).trim()
            return try {
                sub.toFloat()
            } catch (e: Exception) {
                RecorderLog.w(TAG, "exception ignored", e)
                1f
            }
        }
        return 1f
    }
}
