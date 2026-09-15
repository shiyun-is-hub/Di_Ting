package com.diting.recorder.system

import android.media.MediaCodec
import android.os.HandlerThread
import android.view.Surface
import com.diting.recorder.RecorderLog
import com.diting.recorder.audio.AudioCaptureFactory
import com.diting.recorder.capture.DisplayCaptureController
import com.diting.recorder.gl.GlFrameRenderer
import com.diting.recorder.media.SegmentedMp4Muxer

class RecorderResourceManager : AutoCloseable {
    private val TAG = "RecorderResourceManager"
    private val releaseTasks = mutableListOf<() -> Unit>()

    fun addVideoCodec(codec: MediaCodec) {
        releaseTasks.add {
            try { codec.stop() } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
            try { codec.release() } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
        }
    }

    fun addAudioCodec(codec: MediaCodec) {
        releaseTasks.add {
            try { codec.stop() } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
            try { codec.release() } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
        }
    }

    fun addSurface(surface: Surface) {
        releaseTasks.add {
            try { surface.release() } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
        }
    }

    fun addAudioSession(session: AudioCaptureFactory.AudioCaptureSession) {
        releaseTasks.add { session.release() }
    }

    fun addMuxer(muxer: SegmentedMp4Muxer) {
        releaseTasks.add { muxer.stopAndRelease() }
    }

    fun addRenderer(renderer: GlFrameRenderer) {
        releaseTasks.add { renderer.release() }
    }

    fun addCaptureSurface(controller: DisplayCaptureController) {
        releaseTasks.add { controller.releaseCaptureSurface() }
    }

    fun addWakeLock() {
        releaseTasks.add { WakeLockController.release() }
    }

    fun addThread(thread: HandlerThread) {
        releaseTasks.add {
            if (thread != null) {
                try {
                    thread.quitSafely()
                    thread.join(1000)
                } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
            }
        }
    }

    override fun close() {
        for (task in releaseTasks.reversed()) {
            try {
                task()
            } catch (e: Exception) { RecorderLog.w(TAG, "Resource release failed: ${e.message}") }
        }
        releaseTasks.clear()
    }
}
