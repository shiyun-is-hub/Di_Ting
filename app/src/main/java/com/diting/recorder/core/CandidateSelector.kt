package com.diting.recorder.core

import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Pair
import com.diting.recorder.RecorderConfig
import com.diting.recorder.capture.DisplayCaptureController

object CandidateSelector {
    data class VideoCandidate(
        val mimeType: String,
        val width: Int,
        val height: Int,
        val fps: Int,
        val codecLabel: String
    )

    /**
     * 按用户选择的画质/帧率构建候选列表。
     * quality: "1080p" | "720p" | "360p" | "screen"(跟随屏幕)
     * 首选档位被编码器拒绝时，自动降级到更低的档位，保证能录到。
     */
    fun buildVideoCandidates(
        displayController: DisplayCaptureController,
        fps: Int,
        quality: String = RecorderConfig.DEFAULT_QUALITY
    ): List<VideoCandidate> {
        val mimeTypes = arrayOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)

        val primaryLimit = when (quality) {
            RecorderConfig.QUALITY_SCREEN -> 0 // 0 = 直接使用屏幕原始分辨率
            RecorderConfig.QUALITY_720P -> 720
            RecorderConfig.QUALITY_360P -> 360
            else -> 1080
        }

        val primarySize: Pair<Int, Int> = if (primaryLimit == 0) {
            Pair(displayController.screenWidth and -16, displayController.screenHeight and -16)
        } else {
            displayController.computeTargetSize(primaryLimit, false)
        }

        // 候选尺寸：首选档位在前，其余更低档位依序作为降级备选（自动去重）
        val targetSizes = LinkedHashSet<Pair<Int, Int>>()
        targetSizes.add(primarySize)
        val primaryShortEdge = minOf(primarySize.first, primarySize.second)
        for (limit in intArrayOf(1440, 1080, 720, 360)) {
            if (limit < primaryShortEdge) {
                targetSizes.add(displayController.computeTargetSize(limit, false))
            }
        }

        val fpsCaps = if (fps > 30) arrayOf(fps, 30) else arrayOf(fps)

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<VideoCandidate>()

        for (size in targetSizes) {
            if (size.first <= 0 || size.second <= 0) continue
            for (targetFps in fpsCaps) {
                for (mimeType in mimeTypes) {
                    val codecLabel = if (mimeType.contains("hevc")) "H.265" else "H.264"
                    val key = "$mimeType:${size.first}x${size.second}@$targetFps"
                    if (seen.add(key)) {
                        val testFmt = MediaFormat.createVideoFormat(mimeType, size.first, size.second)
                        if (codecList.findEncoderForFormat(testFmt) == null) continue
                        candidates.add(VideoCandidate(mimeType, size.first, size.second, targetFps, codecLabel))
                    }
                }
            }
        }
        return candidates
    }
}