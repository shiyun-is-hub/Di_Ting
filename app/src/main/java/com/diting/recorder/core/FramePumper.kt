package com.diting.recorder.core

import android.content.Context
import android.os.SystemClock
import com.diting.recorder.RecorderLog
import com.diting.recorder.capture.DisplayCaptureController
import com.diting.recorder.gl.GlFrameRenderer
import java.util.concurrent.atomic.AtomicBoolean

class FramePumper(
    private val displayController: DisplayCaptureController,
    private val shellContext: Context,
    private val renderer: GlFrameRenderer,
    private val ptsNormalizer: PtsNormalizer,
    private val bitrateController: BitrateController
) {
    // 修复：将 const val 放入 companion object
    companion object {
        private const val TAG = "ZR.FramePumper"
        private const val FIRST_FRAME_TIMEOUT_MS = 1500L
        private const val MAX_STARTUP_REBINDS = 2
        private const val RUNTIME_FRAME_TIMEOUT_MS = 2000L
        private const val MAX_RUNTIME_REBINDS = 3
    }

    fun pump(recordingActive: AtomicBoolean, fps: Int, initialRotation: Int, targetWidth: Int, targetHeight: Int) {
        var firstFrameDeadline = SystemClock.elapsedRealtime() + FIRST_FRAME_TIMEOUT_MS
        var startupRebinds = 0
        var firstFrameRendered = false
        var lastValidFrameMs = SystemClock.elapsedRealtime()
        var runtimeRebindCount = 0

        while (recordingActive.get()) {
            if (displayController.consumePendingRebind()) {
                renderer.updateSourceSize(displayController.screenWidth, displayController.screenHeight)
                displayController.bindCaptureSurface(shellContext, renderer.inputSurface, targetWidth, targetHeight)
                lastValidFrameMs = SystemClock.elapsedRealtime()
            }

            var rotationFix = (initialRotation - displayController.rotation) * 90f
            if (rotationFix < 0) rotationFix += 360f

            val rawSysNs = System.nanoTime() - ptsNormalizer.globalStartNs
            var videoPtsNs = rawSysNs - ptsNormalizer.audioTimeOffsetNs
            if (videoPtsNs < 0) videoPtsNs = rawSysNs

            renderer.awaitAndDraw(rotationFix, fps, videoPtsNs)

            if (renderer.lastSubmitWasFrame) {
                bitrateController.frameCountSinceCheck++
                lastValidFrameMs = SystemClock.elapsedRealtime()
            }

            if (!firstFrameRendered) {
                if (renderer.lastSubmitWasFrame) {
                    firstFrameRendered = true
                } else if (SystemClock.elapsedRealtime() >= firstFrameDeadline) {
                    if (startupRebinds >= MAX_STARTUP_REBINDS) {
                        error("Timed out waiting for first video frame")
                    }
                    startupRebinds++
                    firstFrameDeadline = SystemClock.elapsedRealtime() + FIRST_FRAME_TIMEOUT_MS
                    renderer.updateSourceSize(displayController.screenWidth, displayController.screenHeight)
                    displayController.bindCaptureSurface(shellContext, renderer.inputSurface, targetWidth, targetHeight)
                }
            } else {
                if (SystemClock.elapsedRealtime() - lastValidFrameMs >= RUNTIME_FRAME_TIMEOUT_MS) {
                    if (runtimeRebindCount >= MAX_RUNTIME_REBINDS) {
                        error("Runtime frame timeout, max rebinds reached")
                    }
                    runtimeRebindCount++
                    RecorderLog.w(TAG, "No frame for $RUNTIME_FRAME_TIMEOUT_MS ms, rebinding surface (retry $runtimeRebindCount)")
                    renderer.updateSourceSize(displayController.screenWidth, displayController.screenHeight)
                    displayController.bindCaptureSurface(shellContext, renderer.inputSurface, targetWidth, targetHeight)
                    lastValidFrameMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }
}
