package com.diting.recorder.media

import android.os.SystemClock

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.diting.recorder.RecorderConfig
import com.diting.recorder.RecorderLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 分段 MP4 封装（ 支持：视频 + 系统声音 + 麦克风 任意数量轨道）。
 */
class SegmentedMp4Muxer(outputDir: String, baseName: String, private val expectedTrackCount: Int) {

    companion object {
        private const val TAG = "SegmentedMp4Muxer"
        private val SEGMENT_DURATION_MS = RecorderConfig.SEGMENT_DURATION_MS
    }

    private val outputDir: String = outputDir
    private val finalOutputPath: String = "$outputDir/$baseName.mp4"
    private val segmentFiles = mutableListOf<String>()

    // trackFormats：轨道句柄（引擎侧持有）→ 格式；trackMap：轨道句柄 → 当前分片内的轨道序号
    private val trackFormats = LinkedHashMap<Int, MediaFormat>()
    private val trackMap = HashMap<Int, Int>()
    private var nextTrackHandle = 0
    private var videoTrackHandle = -1

    @Volatile private var currentMuxer: AsyncMp4MuxerKt? = null
    private var segmentStartTimeMs: Long = 0
    private var segmentIndex = 0
    @Volatile private var isReleased = false

    init {
        File(outputDir).mkdirs()
        startNewSegment()
    }

    @Synchronized
    private fun startNewSegment() {
        val segmentPath = "$outputDir/.tmp_part_$segmentIndex.mp4"
        val newMuxer = AsyncMp4MuxerKt(segmentPath, expectedTrackCount)
        segmentFiles.add(segmentPath)
        segmentStartTimeMs = SystemClock.elapsedRealtime()
        segmentIndex++

        // 重新挂载所有已注册轨道，保持句柄→新分片索引映射
        trackFormats.forEach { (handle, format) ->
            trackMap[handle] = newMuxer.addTrack(format)
        }
        newMuxer.start()
        currentMuxer = newMuxer
    }

    @Synchronized
    fun addTrack(format: MediaFormat): Int {
        val handle = nextTrackHandle++
        trackFormats[handle] = format
        val isVideo = format.getString(MediaFormat.KEY_MIME)?.contains("video") == true
        if (isVideo && videoTrackHandle == -1) videoTrackHandle = handle
        val idx = currentMuxer!!.addTrack(format)
        trackMap[handle] = idx
        return handle
    }

    fun start() {
        // 交由 AsyncMp4MuxerKt 的协程 Channel 接管，无需额外处理
    }

    fun writeSampleData(trackHandle: Int, codecBuffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {
        if (codecBuffer == null || info == null || info.size <= 0 || isReleased) return

        val now = SystemClock.elapsedRealtime()
        if (now - segmentStartTimeMs >= SEGMENT_DURATION_MS) {
            val isVideoTrack = trackHandle == videoTrackHandle
            val isKeyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            if ((isVideoTrack && isKeyFrame) || (now - segmentStartTimeMs >= SEGMENT_DURATION_MS + 5000L)) {
                synchronized(this) {
                    val oldMuxer = currentMuxer
                    startNewSegment()
                    // 异步释放旧分片，确保 MediaCodec 回调线程绝对不被文件 I/O 阻塞
                    CoroutineScope(Dispatchers.IO).launch {
                        try { oldMuxer?.stopAndRelease() } catch (e: Exception) { RecorderLog.w(TAG, "Old segment close error", e) }
                    }
                }
            }
        }

        // 零拷贝中转：直接将原始 ByteBuffer 喂给异步 Muxer，省去一次内存申请和拷贝
        synchronized(this) {
            val realTrack = trackMap[trackHandle]
            if (realTrack != null && realTrack >= 0) {
                currentMuxer?.writeSampleData(realTrack, codecBuffer, info)
            }
        }
    }

    fun stopAndRelease() {
        if (isReleased) return
        isReleased = true
        try { currentMuxer?.stopAndRelease() } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }
        try { mergeSegmentsToFinalFile() } catch (e: Exception) { RecorderLog.w("ZR.Muxer", "Merge failed, segments kept as files") }
    }

    private fun mergeSegmentsToFinalFile() {
        if (segmentFiles.isEmpty()) return

        if (segmentFiles.size == 1) {
            val single = File(segmentFiles[0])
            val target = File(finalOutputPath)
            if (single.exists() && single.length() > 0) {
                target.delete()
                single.renameTo(target)
                try {
                    RandomAccessFile(target, "rw").use { it.fd.sync() }
                } catch (e: Exception) {
                    // 忽略 posix_fadvise 缺失警告，保持日志绝对干净
                }
            }
            return
        }

        val finalMuxer = MediaMuxer(finalOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024) // 使用 DirectBuffer 避免 JNI 层面的 Java Heap 到 Native 内存的拷贝开销
        val info = MediaCodec.BufferInfo()

        // 分片内的轨道序号 → 最终文件轨道序号；以及每条轨道的类型（用于间隙微调）
        val finalTrackIndices = ArrayList<Int>()
        val trackMimes = ArrayList<String>()
        var muxerStarted = false
        // 使用每条轨道各自单调递增的时间戳，摒弃容易导致画面冻结的 Offset 算法
        val lastPts = HashMap<Int, Long>()

        try {
            for (segmentPath in segmentFiles) {
                val segFile = File(segmentPath)
                if (!segFile.exists() || segFile.length() <= 0) continue

                val extractor = MediaExtractor()
                val segmentFis = FileInputStream(segmentPath)
                val fd: FileDescriptor = segmentFis.fd

                try {
                    if (Build.VERSION.SDK_INT >= 21) {
                        val osClass = Class.forName("android.system.Os")
                        val fadvise = osClass.getMethod("posix_fadvise", FileDescriptor::class.java, Long::class.java, Long::class.java, Int::class.java)
                        fadvise.invoke(null, fd, 0L, 0L, 2)
                        fadvise.invoke(null, fd, 0L, 0L, 4)
                    }
                } catch (e: Exception) {
                    // 忽略 posix_fadvise 缺失警告，保持日志绝对干净
                }

                extractor.setDataSource(fd)
                val trackCount = extractor.trackCount

                if (!muxerStarted) {
                    for (i in 0 until trackCount) {
                        val format = extractor.getTrackFormat(i)
                        trackMimes.add(format.getString(MediaFormat.KEY_MIME) ?: "")
                        finalTrackIndices.add(finalMuxer.addTrack(format))
                    }
                    finalMuxer.start()
                    muxerStarted = true
                }

                val usableTracks = minOf(trackCount, finalTrackIndices.size)
                for (i in 0 until usableTracks) extractor.selectTrack(i)

                val segOffset = LongArray(finalTrackIndices.size)
                val isFirstSample = BooleanArray(finalTrackIndices.size) { true }

                while (true) {
                    val trackIndex = extractor.sampleTrackIndex
                    if (trackIndex < 0) break
                    if (trackIndex >= finalTrackIndices.size) {
                        extractor.advance()
                        continue
                    }

                    val sampleSize = extractor.sampleSize
                    if (sampleSize > buffer.capacity()) {
                        // 如果发生极其罕见的超大帧导致超出初始缓冲，进行一次性对齐扩展
                        val maxInput = Math.max(sampleSize.toInt(), 8 * 1024 * 1024)
                        com.diting.recorder.RecorderLog.i(TAG, "Expanding merge buffer to ${maxInput / 1024} KB")
                        buffer = ByteBuffer.allocateDirect(maxInput)
                    }

                    val size = extractor.readSampleData(buffer, 0)
                    val pts = extractor.sampleTime
                    val flags = extractor.sampleFlags

                    if (isFirstSample[trackIndex]) {
                        var offset = (lastPts[trackIndex] ?: 0L) - pts
                        if ((lastPts[trackIndex] ?: 0L) > 0) {
                            val mime = trackMimes.getOrNull(trackIndex) ?: ""
                            offset += if (mime.startsWith("video/")) 16_000L else 23_000L
                        }
                        segOffset[trackIndex] = offset
                        isFirstSample[trackIndex] = false
                    }

                    val writePts = pts + segOffset[trackIndex]
                    val cur = lastPts[trackIndex] ?: 0L
                    if (writePts > cur) lastPts[trackIndex] = writePts
                    info.set(0, size, writePts, flags)
                    finalMuxer.writeSampleData(finalTrackIndices[trackIndex], buffer, info)

                    extractor.advance()
                }

                extractor.release()
                segmentFis.close()
            }
        } finally {
            try { finalMuxer.stop() } catch (e: Exception) {
                com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
            }
            try { finalMuxer.release() } catch (e: Exception) {
                com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
            }
        }

        try {
            RandomAccessFile(finalOutputPath, "rw").use { it.fd.sync() }
        } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }

        // 校验最终文件是否成功生成且大小合法，否则保留切片供后续排错或抢救
        val finalMergedFile = File(finalOutputPath)
        if (!finalMergedFile.exists() || finalMergedFile.length() < 1024) {
            com.diting.recorder.RecorderLog.e(TAG, "Merge failed or file too small. Segments are KEPT in directory.")
            return
        }

        for (segmentPath in segmentFiles) {
            try { File(segmentPath).delete() } catch (e: Exception) {
                com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
            }
        }
    }

    fun getFinalOutputPath(): String = finalOutputPath
}