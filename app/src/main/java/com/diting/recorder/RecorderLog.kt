package com.diting.recorder

import android.util.Log

object RecorderLog {
    @JvmStatic
    @JvmOverloads
    fun v(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) {
            Log.v(tag, msg)
            println("[V] $tag: $msg")
        } else {
            Log.v(tag, msg, tr)
            println("[V] $tag: $msg")
            tr.printStackTrace(System.out)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) {
            Log.d(tag, msg)
            println("[D] $tag: $msg")
        } else {
            Log.d(tag, msg, tr)
            println("[D] $tag: $msg")
            tr.printStackTrace(System.out)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun i(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) {
            Log.i(tag, msg)
            println("[I] $tag: $msg")
        } else {
            Log.i(tag, msg, tr)
            println("[I] $tag: $msg")
            tr.printStackTrace(System.out)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) {
            Log.w(tag, msg)
            System.err.println("[W] $tag: $msg")
        } else {
            Log.w(tag, msg, tr)
            System.err.println("[W] $tag: $msg")
            tr.printStackTrace(System.err)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) {
            Log.e(tag, msg)
            System.err.println("[E] $tag: $msg")
        } else {
            Log.e(tag, msg, tr)
            System.err.println("[E] $tag: $msg")
            tr.printStackTrace(System.err)
        }
    }
}
