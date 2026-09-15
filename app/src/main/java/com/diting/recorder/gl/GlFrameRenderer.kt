package com.diting.recorder.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import com.diting.recorder.RecorderLog
import com.diting.recorder.system.NativeCore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class GlFrameRenderer(initialWidth: Int, initialHeight: Int) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "ZR.GL"
        private const val STALE_FRAME_WARNING_MS = 1000L
        private val FULLSCREEN_QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val FULLSCREEN_UV = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var programId = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    lateinit var inputSurface: Surface

    // 使用高性能 ReentrantLock 替代协程 Channel，消除 GC 导致的掉帧死锁
    private val frameLock = ReentrantLock()
    private val frameCondition = frameLock.newCondition()
    private val frameAvailable = AtomicBoolean(false)

    private val renderThread: HandlerThread
    private var hasReceivedFrame = false

    private val positionBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer

    private val textureMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private var muMVPMatrixHandle = 0
    private var muSTMatrixHandle = 0
    private var maPosHandle = 0
    private var maTexHandle = 0

    private var sourceWidth: Int = initialWidth
    private var sourceHeight: Int = initialHeight
    private var encoderWidth: Int = initialWidth
    private var encoderHeight: Int = initialHeight
    private var viewportDirty = true
    private var programBound = false
    private var lastRotationDegrees = -1f
    @Volatile private var frameCount = 0L
    @Volatile private var lastFrameTimestampMs = 0L
    private var lastStaleWarningMs = 0L
    private var lastSubmitTimestampNs = -1L

    @Volatile
    var lastSubmitWasFrame = false
        private set

    init {
        renderThread = HandlerThread("GLRenderThread", Process.THREAD_PRIORITY_DISPLAY).also { it.start() }
        Handler(renderThread.looper).post {
            if (NativeCore.isAvailable()) {
                NativeCore.enableRealTimeScheduling()
                NativeCore.bindToPerformanceCores()
            }
        }

        positionBuffer = toBuffer(FULLSCREEN_QUAD)
        textureBuffer = toBuffer(FULLSCREEN_UV)
    }

    fun initialize(encoderInputSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configs = arrayOfNulls<EGLConfig>(1)
        val configAttributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 4,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configCount = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, 1, configCount, 0)

        // 如果 GPU 不支持 Recordable 标志，启用降级配置防止 configs[0] 为 null
        if (configCount[0] == 0 || configs[0] == null) {
            val fallbackAttributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 4,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            EGL14.eglChooseConfig(eglDisplay, fallbackAttributes, 0, configs, 0, 1, configCount, 0)
        }

        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, 0x3100, 0x3101, EGL14.EGL_NONE), 0
        )

        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            RecorderLog.w(TAG, "High priority EGL context rejected by GPU, falling back to standard priority.")
            EGL14.eglGetError()
            eglContext = EGL14.eglCreateContext(
                eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
        }

        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], encoderInputSurface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        val vertexShader = compileShader(
            GLES20.GL_VERTEX_SHADER,
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPos;\n" +
            "attribute vec4 aTex;\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPos;\n" +
            "  vTex = (uSTMatrix * aTex).xy;\n" +
            "}"
        )
        val fragmentShader = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "uniform samplerExternalOES s;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(s, vTex);\n" +
            "}"
        )

        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)

        muMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        muSTMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix")
        maPosHandle = GLES20.glGetAttribLocation(programId, "aPos")
        maTexHandle = GLES20.glGetAttribLocation(programId, "aTex")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).also {
            it.setOnFrameAvailableListener(this, Handler(renderThread.looper))
        }
        inputSurface = Surface(surfaceTexture)
    }

    fun setEncoderSize(width: Int, height: Int) {
        encoderWidth = width
        encoderHeight = height
        viewportDirty = true
    }

    fun updateSourceSize(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        surfaceTexture?.setDefaultBufferSize(width, height)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        frameLock.lock()
        try {
            frameAvailable.set(true)
            frameCount++
            lastFrameTimestampMs = SystemClock.elapsedRealtime()
            frameCondition.signalAll()
        } finally {
            frameLock.unlock()
        }
        if (frameCount == 1L) RecorderLog.i(TAG, "First source frame arrived")
    }

    fun awaitAndDraw(rotationDegrees: Float, targetFps: Int, presentationTimeNs: Long): Boolean {
        val waitTimeoutMs = 1000L / Math.max(targetFps, 1)
        val adjustedTimeout = if (hasReceivedFrame) waitTimeoutMs else 1000L
        var hasNewFrame = false
        lastSubmitWasFrame = false

        frameLock.lock()
        try {
            if (!frameAvailable.get()) {
                try {
                    frameCondition.await(adjustedTimeout, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            if (frameAvailable.getAndSet(false)) {
                hasNewFrame = true
            }
        } finally {
            frameLock.unlock()
        }

        var canSubmit = false
        if (hasNewFrame) {
            try {
                surfaceTexture?.updateTexImage()
                hasReceivedFrame = true
                canSubmit = true
            } catch (e: Exception) {
                java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L)
                return false
            }
        } else if (hasReceivedFrame) {
            val now = SystemClock.elapsedRealtime()
            val needsKeepAlive = lastFrameTimestampMs > 0 &&
                now - lastFrameTimestampMs >= STALE_FRAME_WARNING_MS &&
                now - lastStaleWarningMs >= STALE_FRAME_WARNING_MS
            canSubmit = needsKeepAlive
        }

        if (!canSubmit) return false

        val now = SystemClock.elapsedRealtime()
        if (!hasNewFrame && lastFrameTimestampMs > 0 &&
            now - lastFrameTimestampMs >= STALE_FRAME_WARNING_MS &&
            now - lastStaleWarningMs >= STALE_FRAME_WARNING_MS) {
            lastStaleWarningMs = now
            RecorderLog.w(TAG, "No new source frame for ${now - lastFrameTimestampMs}ms; reusing previous frame (throttled)")
        }

        surfaceTexture?.getTransformMatrix(textureMatrix)

        if (viewportDirty) {
            GLES20.glViewport(0, 0, encoderWidth, encoderHeight)
            viewportDirty = false
        }

        // 每次绘制前清空颜色缓冲区。
        // 防止屏幕旋转出现黑边时，边缘区域残留上一帧的残影画面。
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (!programBound) {
            GLES20.glUseProgram(programId)
            programBound = true
        }

        if (viewportDirty || Math.abs(rotationDegrees - lastRotationDegrees) > 0.1f) {
            lastRotationDegrees = rotationDegrees
            val effectiveWidth = if (rotationDegrees == 90f || rotationDegrees == 270f) sourceHeight.toFloat() else sourceWidth.toFloat()
            val effectiveHeight = if (rotationDegrees == 90f || rotationDegrees == 270f) sourceWidth.toFloat() else sourceHeight.toFloat()

            val sourceAspect = effectiveWidth / effectiveHeight
            val encoderAspect = encoderWidth.toFloat() / encoderHeight.toFloat()
            var scaleX = 1f
            var scaleY = 1f

            if (sourceAspect > encoderAspect) {
                scaleX = sourceAspect / encoderAspect
            } else {
                scaleY = encoderAspect / sourceAspect
            }

            Matrix.setIdentityM(mvpMatrix, 0)
            Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1f)
            if (rotationDegrees != 0f) {
                Matrix.rotateM(mvpMatrix, 0, rotationDegrees, 0f, 0f, 1f)
            }
        }

        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, textureMatrix, 0)

        GLES20.glEnableVertexAttribArray(maPosHandle)
        GLES20.glVertexAttribPointer(maPosHandle, 2, GLES20.GL_FLOAT, false, 8, positionBuffer)

        GLES20.glEnableVertexAttribArray(maTexHandle)
        GLES20.glVertexAttribPointer(maTexHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)

        lastSubmitTimestampNs = presentationTimeNs
        lastSubmitWasFrame = true
        return hasNewFrame
    }

    fun release() {
        try {
            if (eglContext != EGL14.EGL_NO_CONTEXT && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            }
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            }
            // 必须先从 EGL 状态机中解绑并销毁 EGLSurface
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)

            // 然后再销毁上层 Java 的 Surface 与 SurfaceTexture
            inputSurface.release()
            surfaceTexture?.release()
            renderThread.quitSafely()
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        } catch (e: Exception) {
            RecorderLog.w(TAG, "EGL release error: ${e.message}")
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun toBuffer(values: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(values)
            .apply { position(0) }
    }
}
