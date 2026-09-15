package com.diting.recorder.ui

import android.os.SystemClock

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.diting.recorder.RecorderConfig
import com.diting.recorder.RecorderLog
import java.io.File
import java.lang.reflect.Method
import java.util.Locale

@SuppressLint("PrivateApi")
class FloatingMenuController {

    companion object {
        private const val TAG = "FloatingMenuController"

        val ITEM_SIZE: Int by lazy {
            val density = android.content.res.Resources.getSystem().displayMetrics.density
            (RecorderConfig.FLOAT_ITEM_SIZE_DP * density + 0.5f).toInt()
        }
    }

    @Volatile private var isReleased = false
    private var surfaceControl: Any? = null
    private var surface: Surface? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var setPosMethod: Method? = null
    private var applyMethod: Method? = null
    private var releaseMethod: Method? = null
    private var cachedTx: Any? = null

    private var recordStartTime: Long = 0
    private var isCurrentlyRecording = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val panelRect = RectF()
    private val fontMetrics = Paint.FontMetrics()

    private val timerRunnable: Runnable = object : Runnable { override fun run() {
        if (isCurrentlyRecording) {
            drawMenuInternal()
            uiHandler.postDelayed(this, 1000)
        } }
    }

    fun getCurrentWidth(isRecording: Boolean): Int = if (isRecording) ITEM_SIZE * 2 else ITEM_SIZE * 3

    fun getCurrentHeight(): Int = ITEM_SIZE

    fun init(x: Int, y: Int) {
        try {
            val m = Typeface::class.java.getDeclaredMethod("loadPreinstalledSystemFontMap")
            m.isAccessible = true
            m.invoke(null)
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }

        try {
            val font = File("/system/fonts/Roboto-Regular.ttf")
            if (font.exists()) {
                paint.typeface = Typeface.createFromFile(font)
            } else {
                paint.typeface = Typeface.DEFAULT_BOLD
            }
        } catch (e: Exception) {
            paint.typeface = Typeface.DEFAULT_BOLD
        }

        try {
            val scClass = Class.forName("android.view.SurfaceControl")
            val builderClass = Class.forName("android.view.SurfaceControl\$Builder")
            val builder = builderClass.getConstructor().newInstance()

            builderClass.getMethod("setName", String::class.java).invoke(builder, "Diting_FloatingMenu")
            builderClass.getMethod("setBufferSize", Int::class.java, Int::class.java).invoke(builder, ITEM_SIZE * 3, ITEM_SIZE)
            builderClass.getMethod("setFormat", Int::class.java).invoke(builder, -3)

            surfaceControl = builderClass.getMethod("build").invoke(builder)

            val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val tx = txClass.getConstructor().newInstance()
            cachedTx = tx
            setPosMethod = txClass.getMethod("setPosition", scClass, Float::class.java, Float::class.java)
            applyMethod = txClass.getMethod("apply")
            releaseMethod = scClass.getMethod("release")

            txClass.getMethod("setLayer", scClass, Int::class.java).invoke(tx, surfaceControl, Int.MAX_VALUE)
            txClass.getMethod("setPosition", scClass, Float::class.java, Float::class.java).invoke(tx, surfaceControl, x.toFloat(), y.toFloat())
            txClass.getMethod("setVisibility", scClass, Boolean::class.java).invoke(tx, surfaceControl, true)
            txClass.getMethod("apply").invoke(tx)

            surface = Surface::class.java.getConstructor().newInstance() as Surface
            Surface::class.java.getMethod("copyFrom", scClass).invoke(surface, surfaceControl)
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught", e)
        }
    }

    fun setPosition(x: Int, y: Int) {
        if (isReleased) return
        try {
            if (setPosMethod != null && applyMethod != null && cachedTx != null) {
                setPosMethod!!.invoke(cachedTx, surfaceControl, x.toFloat(), y.toFloat())
                applyMethod!!.invoke(cachedTx)
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }

    fun drawMenu(isRecording: Boolean) {
        uiHandler.post {
            if (isRecording && !isCurrentlyRecording) {
                recordStartTime = SystemClock.elapsedRealtime()
                isCurrentlyRecording = true
                uiHandler.post(timerRunnable)
            } else if (!isRecording && isCurrentlyRecording) {
                isCurrentlyRecording = false
                uiHandler.removeCallbacks(timerRunnable)
            }
            drawMenuInternal()
        }
    }

    @Synchronized
    private fun drawMenuInternal() {
        if (surface == null || !surface!!.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = surface!!.lockCanvas(null)
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                val width = getCurrentWidth(isCurrentlyRecording)
                val cy = ITEM_SIZE / 2
                val radius = (ITEM_SIZE / 2) - 20

                val bgAlpha = if (isCurrentlyRecording) 0x44 else 0xDD

                paint.color = Color.argb(0x66, 0, 0, 0)
                panelRect.set(8f, 10f, (width - 8).toFloat(), (ITEM_SIZE - 8).toFloat())
                canvas.drawRoundRect(panelRect, ITEM_SIZE / 2.2f, ITEM_SIZE / 2.2f, paint)

                paint.color = Color.argb(bgAlpha, 20, 20, 24)
                panelRect.set(8f, 8f, (width - 8).toFloat(), (ITEM_SIZE - 10).toFloat())
                canvas.drawRoundRect(panelRect, ITEM_SIZE / 2.2f, ITEM_SIZE / 2.2f, paint)

                val strokeAlpha = if (isCurrentlyRecording) 0x22 else 0x66
                paint.color = Color.argb(strokeAlpha, 255, 255, 255)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawRoundRect(panelRect, ITEM_SIZE / 2f, ITEM_SIZE / 2f, paint)
                paint.style = Paint.Style.FILL

                if (!isCurrentlyRecording) {
                    paint.color = Color.parseColor("#EF4444")
                    canvas.drawCircle((ITEM_SIZE / 2).toFloat(), cy.toFloat(), radius.toFloat(), paint)

                    val midX = ITEM_SIZE + ITEM_SIZE / 2
                    paint.color = Color.WHITE
                    canvas.drawCircle((midX - 15).toFloat(), cy.toFloat(), 5f, paint)
                    canvas.drawCircle(midX.toFloat(), cy.toFloat(), 5f, paint)
                    canvas.drawCircle((midX + 15).toFloat(), cy.toFloat(), 5f, paint)

                    val rightX = ITEM_SIZE * 2 + ITEM_SIZE / 2
                    paint.color = Color.WHITE
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 5f
                    paint.strokeCap = Paint.Cap.ROUND
                    canvas.drawLine((rightX - 15).toFloat(), (cy - 15).toFloat(), (rightX + 15).toFloat(), (cy + 15).toFloat(), paint)
                    canvas.drawLine((rightX - 15).toFloat(), (cy + 15).toFloat(), (rightX + 15).toFloat(), (cy - 15).toFloat(), paint)
                    paint.style = Paint.Style.FILL
                } else {
                    val firstCx = ITEM_SIZE / 2
                    val secondCx = ITEM_SIZE + ITEM_SIZE / 2

                    paint.color = Color.argb(0x22, 0, 0, 0)
                    canvas.drawCircle(firstCx.toFloat(), cy.toFloat(), (radius + 10).toFloat(), paint)

                    paint.color = Color.argb(0x66, 0, 230, 118)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    canvas.drawCircle(firstCx.toFloat(), cy.toFloat(), (radius + 10).toFloat(), paint)
                    paint.style = Paint.Style.FILL

                    paint.color = Color.parseColor("#10B981")
                    panelRect.set((firstCx - 16).toFloat(), (cy - 16).toFloat(), (firstCx + 16).toFloat(), (cy + 16).toFloat())
                    canvas.drawRoundRect(panelRect, 6f, 6f, paint) // 复用对象，实现 UI 零分配渲染

                    val elapsedSec = (SystemClock.elapsedRealtime() - recordStartTime) / 1000
                    val min = elapsedSec / 60
                    val sec = elapsedSec % 60
                    // 使用纯字符拼接替代 String.format 正则匹配，避免高频触发 Minor GC
                    val timeStr = "${if (min < 10) "0" else ""}$min:${if (sec < 10) "0" else ""}$sec"

                    paint.color = Color.argb(0xEE, 255, 255, 255)
                    paint.textSize = ITEM_SIZE * 0.25f
                    paint.isFakeBoldText = true
                    paint.textAlign = Paint.Align.CENTER
                    paint.getFontMetrics(fontMetrics)
                    canvas.drawText(timeStr, secondCx.toFloat(), cy - (fontMetrics.descent + fontMetrics.ascent) / 2, paint)
                }
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught", e)
        } finally {
            if (canvas != null) {
                try {
                    if (surface != null && surface!!.isValid) {
                        surface!!.unlockCanvasAndPost(canvas)
                    }
                } catch (t: Throwable) {
                    // 拦截退出时的竞态报错，保证进程优雅结束
                }
            }
        }
    }

    @Synchronized
    fun release() {
        isReleased = true
        isCurrentlyRecording = false
        uiHandler.removeCallbacks(timerRunnable)
        try {
            surface?.release()
            surface = null
            if (surfaceControl != null) {
                releaseMethod?.invoke(surfaceControl)
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }
}
