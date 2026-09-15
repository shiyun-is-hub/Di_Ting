package com.diting.recorder.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.diting.recorder.RecorderLog
import java.io.File

object MediaStoreExporter {
    private const val TAG = "ZR.MediaStore"
    private const val DEFAULT_RELATIVE_DIR = "Movies/Diting"

    /**
     * 方式一：通知媒体库扫描已存在的文件。
     * 适用于文件已写入公共目录（如 /sdcard/Movies/Diting/xxx.mp4）的情况。
     * 扫描完成后回调 uri 不为 null 即表示成功。
     */
    fun scanFile(context: Context, file: File): Uri? {
        if (!file.exists() || file.length() <= 0) return null
        var result: Uri? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                RecorderLog.i(TAG, "scan completed: path=$path uri=$uri")
                result = uri
                latch.countDown()
            }
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (t: Throwable) {
            RecorderLog.e(TAG, "scanFile failed: ${t.message}")
        }
        return result
    }

    /**
     * 方式二：通过 MediaStore 将文件导出到相册可见目录。
     * 使用 IS_PENDING 标记保证写入期间对相册不可见，写完再置 0 放行。
     * 适用于 Android 10（Q）及以上。
     */
    fun exportViaMediaStore(
        context: Context,
        src: File,
        displayName: String,
        relativeDir: String = DEFAULT_RELATIVE_DIR
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (!src.exists() || src.length() <= 0) return null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        var uri: Uri? = null
        try {
            uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val out = resolver.openOutputStream(uri) ?: throw IllegalStateException("无法打开输出流")
            out.use { o ->
                src.inputStream().use { input -> input.copyTo(o) }
            }
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            RecorderLog.i(TAG, "exported to MediaStore: $uri")
            return uri
        } catch (t: Throwable) {
            RecorderLog.e(TAG, "exportViaMediaStore failed: ${t.message}")
            if (uri != null) {
                try { resolver.delete(uri, null, null) } catch (e: Throwable) {}
            }
            return null
        }
    }

    /**
     * 把 outputDir 形式的绝对路径转换为 MediaStore 的相对路径。
     * 例：/sdcard/Movies/Diting -> Movies/Diting
     */
    fun toRelativeDir(outputDir: String): String {
        val prefixes = arrayOf(
            "/storage/emulated/0/",
            Environment.getExternalStorageDirectory().absolutePath + "/"
        )
        for (prefix in prefixes) {
            if (outputDir.startsWith(prefix)) {
                val rel = outputDir.removePrefix(prefix).trim('/')
                return if (rel.isEmpty()) DEFAULT_RELATIVE_DIR else rel
            }
        }
        return DEFAULT_RELATIVE_DIR
    }
}