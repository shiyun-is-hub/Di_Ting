package com.diting.recorder.media

import com.diting.recorder.RecorderLog
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

object SharedBufferPool {

    private const val LARGE_BUFFER_SIZE = 512 * 1024
    private const val SMALL_BUFFER_SIZE = 64 * 1024 // 与 Muxer 的 64KB 阈值对齐，减少高频 P 帧引发的内存抖动
    private const val MAX_LARGE_BUFFERS = 64
    private const val MAX_SMALL_BUFFERS = 256
    private const val ALLOC_WARN_THRESHOLD = 100

    private val largeBuffers = ArrayBlockingQueue<ByteBuffer>(MAX_LARGE_BUFFERS)
    private val smallBuffers = ArrayBlockingQueue<ByteBuffer>(MAX_SMALL_BUFFERS)
    private val activeConsumers = AtomicInteger(0)
    private val largeAllocCount = AtomicInteger(0)
    private val smallAllocCount = AtomicInteger(0)

    // 移除 init 块中的 40MB 内存预分配，改为首次请求时懒加载分配

    fun acquireSession(): PoolSession {
        activeConsumers.incrementAndGet()
        return PoolSession()
    }

    fun borrowLargeBuffer(): ByteBuffer {
        return largeBuffers.poll() ?: run {
            val count = largeAllocCount.incrementAndGet()
            if (count > ALLOC_WARN_THRESHOLD) {
                RecorderLog.w("ZR.BufferPool", "Large buffer alloc warning: count=$count")
            }
            ByteBuffer.allocateDirect(LARGE_BUFFER_SIZE)
        }
    }

    fun borrowSmallBuffer(): ByteBuffer {
        return smallBuffers.poll() ?: run {
            val count = smallAllocCount.incrementAndGet()
            if (count > ALLOC_WARN_THRESHOLD) {
                RecorderLog.w("ZR.BufferPool", "Small buffer alloc warning: count=$count")
            }
            ByteBuffer.allocateDirect(SMALL_BUFFER_SIZE)
        }
    }

    fun returnLargeBuffer(buf: ByteBuffer?) {
        if (buf == null) return
        buf.clear()
        if (largeBuffers.size < MAX_LARGE_BUFFERS) {
            largeBuffers.offer(buf)
        }
    }

    fun returnSmallBuffer(buf: ByteBuffer?) {
        if (buf == null) return
        buf.clear()
        if (smallBuffers.size < MAX_SMALL_BUFFERS) {
            smallBuffers.offer(buf)
        }
    }

    class PoolSession : AutoCloseable {
        override fun close() {
            if (activeConsumers.decrementAndGet() == 0) {
                RecorderLog.i("ZR.BufferPool", "All sessions closed")
            }
        }
    }
}
