package com.diting.recorder.capture

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Pair
import android.view.Surface
import com.diting.recorder.RecorderLog
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class DisplayCaptureController(private val displayManagerGlobal: Any) {

    companion object {
        private const val TAG = "ZR.Display"

        private var sCreateDisplayMethod: Method? = null
        private var sDestroyDisplayMethod: Method? = null
        private var sTransactionConstructor: Constructor<*>? = null
        private var sSetDisplaySurfaceMethod: Method? = null
        private var sSetDisplayProjectionMethod: Method? = null
        private var sSetDisplayLayerStackMethod: Method? = null
        private var sApplyMethod: Method? = null
        private var sIsSurfaceControlSupported = false

        init {
            try {
                val classLoader = DisplayCaptureController::class.java.classLoader
                val displayClasses = arrayOf(
                    "com.android.server.display.DisplayControl",
                    "android.window.DisplayControl",
                    "android.window.ScreenCapture",
                    "android.view.DisplayControl",
                    "android.view.SurfaceControl"
                )

                for (className in displayClasses) {
                    try {
                        val clazz = classLoader!!.loadClass(className)
                        for (method in clazz.declaredMethods) {
                            if (method.name == "createDisplay") {
                                sCreateDisplayMethod = method.also { it.isAccessible = true }
                                break
                            }
                        }
                        if (sCreateDisplayMethod != null) {
                            for (method in clazz.declaredMethods) {
                                if (method.name == "destroyDisplay") {
                                    sDestroyDisplayMethod = method.also { it.isAccessible = true }
                                    break
                                }
                            }
                            break
                        }
                    } catch (e: Throwable) {}
                }

                if (sCreateDisplayMethod != null) {
                    val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
                    sTransactionConstructor = txClass.getConstructor()
                    sSetDisplaySurfaceMethod = txClass.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java)
                    sSetDisplayProjectionMethod = txClass.getMethod("setDisplayProjection", IBinder::class.java, Int::class.java, Rect::class.java, Rect::class.java)
                    sSetDisplayLayerStackMethod = txClass.getMethod("setDisplayLayerStack", IBinder::class.java, Int::class.java)
                    sApplyMethod = txClass.getMethod("apply")
                    sIsSurfaceControlSupported = true
                }
            } catch (t: Throwable) {
                sIsSurfaceControlSupported = false
            }
        }
    }

    private var sGetDisplayInfoMethod: Method? = null
    private val needsRebind = AtomicBoolean(false)

    @Volatile var screenWidth: Int = 0
        private set
    @Volatile var screenHeight: Int = 0
        private set
    @Volatile var rotation: Int = 0
        private set
    @Volatile private var layerStack: Int = 0

    private var lastWidth = 0
    private var lastHeight = 0
    private var lastRotation = -1
    private var displayToken: IBinder? = null
    private var virtualDisplay: VirtualDisplay? = null

    private fun getGetDisplayInfoMethod(): Method? {
        if (sGetDisplayInfoMethod == null) {
            try {
                sGetDisplayInfoMethod = displayManagerGlobal.javaClass.getMethod("getDisplayInfo", Int::class.java)
                sGetDisplayInfoMethod!!.isAccessible = true
            } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
        }
        return sGetDisplayInfoMethod
    }

    fun refreshDisplayInfo() {
        try {
            val method = getGetDisplayInfoMethod() ?: return
            val displayInfo = method.invoke(displayManagerGlobal, 0) ?: return
            screenWidth = readIntField(displayInfo, "logicalWidth")
            screenHeight = readIntField(displayInfo, "logicalHeight")
            rotation = readIntField(displayInfo, "rotation")
            layerStack = readIntField(displayInfo, "layerStack")

            if (screenWidth != lastWidth || screenHeight != lastHeight || rotation != lastRotation) {
                if (lastRotation != -1) needsRebind.set(true)
                lastWidth = screenWidth
                lastHeight = screenHeight
                lastRotation = rotation
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }

    fun registerDisplayListener(handler: Handler) {
        try {
            val listenerClass = Class.forName("android.hardware.display.DisplayManager\$DisplayListener")
            val listener = Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { _, method, _ ->
                if (method.name == "onDisplayChanged") refreshDisplayInfo()
                null
            }
            // FIXME: 每次注册监听都会遍历反射方法数组。建议将 registerDisplayListener 方法提取到 companion object 进行全局静态缓存。
            for (method in displayManagerGlobal.javaClass.declaredMethods) {
                if (method.name != "registerDisplayListener") continue
                val count = method.parameterTypes.size
                if (count == 2) method.invoke(displayManagerGlobal, listener, handler)
                else if (count == 3) method.invoke(displayManagerGlobal, listener, handler, 7L)
                else if (count == 4) method.invoke(displayManagerGlobal, listener, handler, 7L, "com.android.shell")
                break
            }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
    }

    fun computeTargetSize(sizeLimit: Int, limitLongEdge: Boolean): Pair<Int, Int> {
        val sourceLongEdge = maxOf(screenWidth, screenHeight)
        val sourceShortEdge = minOf(screenWidth, screenHeight)
        val scale = if (limitLongEdge) (if (sourceLongEdge > sizeLimit) sizeLimit.toFloat() / sourceLongEdge else 1f)
                    else (if (sourceShortEdge > sizeLimit) sizeLimit.toFloat() / sourceShortEdge else 1f)
        return Pair((screenWidth * scale).roundToInt() and -16, (screenHeight * scale).roundToInt() and -16)
    }

    fun detectRefreshRate(): Int {
        return try {
            val method = getGetDisplayInfoMethod() ?: return 60
            val displayInfo = method.invoke(displayManagerGlobal, 0)
            val mode = displayInfo.javaClass.getMethod("getMode").invoke(displayInfo)
            val refreshRate = mode.javaClass.getMethod("getRefreshRate").invoke(mode) as Float
            minOf(refreshRate.roundToInt(), 60)
        } catch (e: Exception) { 60 }
    }

    fun bindCaptureSurface(context: Context, surface: Surface, encoderWidth: Int, encoderHeight: Int) {
        releaseCaptureSurface()
        displayToken = tryCreateSurfaceControlDisplay(surface, screenWidth, screenHeight, encoderWidth, encoderHeight, layerStack)
        if (displayToken == null) {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            // 恢复为 16 (AUTO_MIRROR)
            virtualDisplay = displayManager.createVirtualDisplay("ZeroCapture", screenWidth, screenHeight, 240, surface, 16)
        }
    }

    fun consumePendingRebind(): Boolean = needsRebind.getAndSet(false)

    fun releaseCaptureSurface() {
        if (displayToken != null) {
            try { sDestroyDisplayMethod?.invoke(null, displayToken) } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
            displayToken = null
        }
        virtualDisplay?.apply { surface = null; release() }
        virtualDisplay = null
    }

    private fun readIntField(target: Any, fieldName: String): Int {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.getInt(target)
    }

    private fun tryCreateSurfaceControlDisplay(surface: Surface, sourceWidth: Int, sourceHeight: Int, encoderWidth: Int, encoderHeight: Int, layerStack: Int): IBinder? {
        if (!sIsSurfaceControlSupported) return null
        return try {
            // 动态判定 createDisplay 的参数数量，适配 Android 10 到 Android 15+ 的隐藏 API 变更
            val paramCount = sCreateDisplayMethod!!.parameterTypes.size
            val token = when (paramCount) {
                2 -> sCreateDisplayMethod!!.invoke(null, "ZeroCapture", false)
                3 -> sCreateDisplayMethod!!.invoke(null, "ZeroCapture", false, 0f)
                4 -> sCreateDisplayMethod!!.invoke(null, "ZeroCapture", false, 0f, 0)
                5 -> sCreateDisplayMethod!!.invoke(null, "ZeroCapture", false, 0f, 0, 0)
                else -> sCreateDisplayMethod!!.invoke(null, "ZeroCapture", false) // 兜底降级
            } as? IBinder ?: return null
            val transaction = sTransactionConstructor!!.newInstance()
            sSetDisplaySurfaceMethod!!.invoke(transaction, token, surface)
            sSetDisplayProjectionMethod!!.invoke(transaction, token, 0, Rect(0, 0, sourceWidth, sourceHeight), Rect(0, 0, encoderWidth, encoderHeight))
            sSetDisplayLayerStackMethod!!.invoke(transaction, token, layerStack)
            sApplyMethod!!.invoke(transaction)
            token
        } catch (e: Exception) { null }
    }
}
