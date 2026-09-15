package com.diting.recorder.core

import android.media.MediaCodec

class PtsNormalizer {
    @Volatile private var globalBaseTimeUs: Long = -1L
    @Volatile var globalStartNs: Long = -1L
    @Volatile var audioTimeOffsetNs: Long = 0L

    fun reset() {
        globalBaseTimeUs = -1L
        globalStartNs = System.nanoTime()
        audioTimeOffsetNs = 0L
    }

    fun normalizeTimeBase(info: MediaCodec.BufferInfo) {
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            info.presentationTimeUs = 0
            return
        }

        if (globalBaseTimeUs == -1L) {
            synchronized(this) {
                if (globalBaseTimeUs == -1L) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                        globalBaseTimeUs = info.presentationTimeUs
                    } else {
                        info.size = 0
                        return
                    }
                }
            }
        }

        info.presentationTimeUs -= globalBaseTimeUs
        if (info.presentationTimeUs < 0) info.presentationTimeUs = 0
    }
}
