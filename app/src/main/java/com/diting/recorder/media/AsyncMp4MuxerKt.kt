package com.diting.recorder.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.io.File
import java.nio.ByteBuffer

class AsyncMp4MuxerKt(
    private val finalOutputPath: String,
    private val expectedTrackCount: Int
) {
    private val tempOutputPath = "$finalOutputPath.tmp"
    private var mediaMuxer: MediaMuxer? = null

    private val frameChannel = Channel<EncodedFrame>(300)
    private var trackCount = 0
    private var muxerStarted = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var consumerJob: Job? = null // 单独记录消费者 Job

    init {
        File(tempOutputPath).delete()
        mediaMuxer = MediaMuxer(tempOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    @Synchronized
    fun addTrack(format: MediaFormat): Int {
        val index = mediaMuxer?.addTrack(format) ?: -1
        trackCount++
        if (trackCount == expectedTrackCount && !muxerStarted) {
            mediaMuxer?.start()
            muxerStarted = true
        }
        return index
    }

    fun start() {
        // 将启动的子协程赋值给 consumerJob，以便安全等待它结束
        consumerJob = scope.launch {
            // 移除 URGENT_DISPLAY 提权，防止污染 Dispatchers.IO 全局线程池，让 Muxer 回归正常 I/O 调度
            frameChannel.consumeEach { frame ->
                while (!muxerStarted && isActive) {
                    delay(10)
                }
                if (frame.info.size > 0 && frame.trackIndex >= 0) {
                    try {
                        mediaMuxer?.writeSampleData(frame.trackIndex, frame.buffer, frame.info)
                    } catch (e: Exception) {
                        Log.e("ZR.MuxerKt", "Muxer write error", e)
                    }
                }
                if (frame.isSmall) SharedBufferPool.returnSmallBuffer(frame.buffer)
                else SharedBufferPool.returnLargeBuffer(frame.buffer)
            }
        }
    }

    fun writeSampleData(trackIndex: Int, codecBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0 || trackIndex < 0) return

        val isSmall = info.size <= 64 * 1024
        val copyBuffer = if (isSmall) SharedBufferPool.borrowSmallBuffer()
                         else SharedBufferPool.borrowLargeBuffer()

        val requiredCapacity = info.size + 10240
        val finalBuffer = if (copyBuffer.capacity() < requiredCapacity) {
            if (isSmall) SharedBufferPool.returnSmallBuffer(copyBuffer)
            else SharedBufferPool.returnLargeBuffer(copyBuffer)
            ByteBuffer.allocateDirect(requiredCapacity)
        } else copyBuffer

        finalBuffer.clear()
        codecBuffer.position(info.offset)
        codecBuffer.limit(info.offset + info.size)
        finalBuffer.put(codecBuffer)
        finalBuffer.flip()

        val bufferInfo = MediaCodec.BufferInfo().apply {
            set(0, info.size, info.presentationTimeUs, info.flags)
        }

        val frame = EncodedFrame(trackIndex, finalBuffer, isSmall, bufferInfo)

        val result = frameChannel.trySend(frame)
        if (result.isFailure) {
            val isKeyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            if (!isKeyFrame && info.size > 10 * 1024) {
                Log.w("ZR.MuxerKt", "I/O bottleneck! Dropped non-key frame (${info.size} bytes).")
                if (isSmall) SharedBufferPool.returnSmallBuffer(finalBuffer)
                else SharedBufferPool.returnLargeBuffer(finalBuffer)
                return
            }
            // 严禁在 MediaCodec 的硬件回调线程中使用 runBlocking 阻塞等待！
            // 发生 I/O 瓶颈导致 Channel 溢出时，立即丢帧，死保编码器流水线的绝对畅通。
            Log.e("ZR.MuxerKt", "I/O bottleneck! Channel full, immediately dropped critical frame to prevent Codec thread starvation.")
            if (isSmall) SharedBufferPool.returnSmallBuffer(finalBuffer)
            else SharedBufferPool.returnLargeBuffer(finalBuffer)
        }
    }

    fun stopAndRelease() {
        runBlocking {
            frameChannel.close() // 1. 发送关闭信号，阻止新数据进入
            consumerJob?.join()  // 2. 仅等待当前消费者处理完旧数据，避免父 Job 死锁
            scope.cancel()       // 3. 彻底释放资源
        }

        if (muxerStarted) {
            try { mediaMuxer?.stop() } catch (e: Exception) {
                com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
            }
        }
        try { mediaMuxer?.release() } catch (e: Exception) {
            com.diting.recorder.RecorderLog.e("ZR.Error", "Exception caught: ${e.message}", e)
        }

        val tempFile = File(tempOutputPath)
        val finalFile = File(finalOutputPath)
        if (tempFile.exists() && tempFile.length() > 1024) {
            finalFile.delete()
            tempFile.renameTo(finalFile)
        } else {
            tempFile.delete()
        }
    }

    private class EncodedFrame(
        val trackIndex: Int,
        val buffer: ByteBuffer,
        val isSmall: Boolean,
        val info: MediaCodec.BufferInfo
    )
}
