package com.diting.recorder

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * 版本更新公告弹窗。
 * 仅在版本升级后首次启动时展示一次（按 versionCode 记录）。
 */
object UpdateNotice {

    private const val PREFS = "diting_update_notice"
    private const val KEY_SEEN_VERSION = "seen_version_code"

    // -------------------- 公告内容（改这里即可） --------------------

    private const val TITLE = "更新公告"

    private const val HEAD = "DiTing-谛听录屏\nv:1.1.1 更新公告\n"

    private const val GREETING =
        "注重个人隐私的使用者们，谛听开发者向你们致以诚挚问候。并为你们带来新版本的谛听录屏。\n"

    private const val SECTION = "我干了什么？\n"

    private const val ITEM1 = "1. 修复相册无法查看录屏的bug\n"

    private const val QA =
        "   Q：为什么会这样？\n" +
            "   A：增强录屏模式在保存视频时直接写入存储目录，没有通知系统媒体库，因此相册扫描不到；本版本已改为通过 MediaStore 登记，录完即可在相册看到。\n"

    private const val ITEM2 = "2. 新增UI美化\n"

    private const val BEAUTIFY =
        "   新增底部导航栏（主页 / 设置），页面切换更顺手；" +
            "主页右上角新增公告入口；新增点击动画与分层浮层底栏。\n"

    private const val CLOSING =
        "感谢各位使用者的支持，请相信谛听录屏与开发者——我们将继续完善谛听录屏。\n"

    private const val SIGNATURE = "- DT开发者\n2026-9-14"

    // -------------------- 对外入口 --------------------

    /**
     * 若当前 versionCode 不等于上次已读版本，则弹窗并在关闭后记录。
     * 每个版本只弹一次。
     */
    fun showIfNeeded(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SEEN_VERSION, -1) == versionCode) return
        show(context) {
            prefs.edit().putInt(KEY_SEEN_VERSION, versionCode).apply()
        }
    }

    /** 手动展示（右上角公告入口调用，不影响"已读"记录）。 */
    fun show(context: Context, onDismiss: (() -> Unit)? = null) {
        val tv = TextView(context).apply {
            text = buildContent()
            textSize = 14f
            setLineSpacing(dp(context, 4).toFloat(), 1f)
            setPadding(dp(context, 22), dp(context, 16), dp(context, 22), dp(context, 16))
        }

        val sv = ScrollView(context).apply {
            addView(tv)
        }

        AlertDialog.Builder(context)
            .setTitle(TITLE)
            .setView(sv)
            .setPositiveButton("知道了") { d, _ ->
                d.dismiss()
                onDismiss?.invoke()
            }
            .setCancelable(false)
            .show()
    }

    /** 拼内容（标题加粗，正文常规）。 */
    private fun buildContent(): CharSequence {
        val sb = SpannableStringBuilder()

        val boldStart = sb.length
        sb.append(HEAD)
        sb.setSpan(
            StyleSpan(Typeface.BOLD), boldStart, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.append("\n")

        sb.append(GREETING)
        sb.append("\n")

        val sectionStart = sb.length
        sb.append(SECTION)
        sb.setSpan(
            StyleSpan(Typeface.BOLD), sectionStart, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.append("\n")

        sb.append(ITEM1)
        sb.append(QA)
        sb.append("\n")
        sb.append(ITEM2)
        sb.append(BEAUTIFY)
        sb.append("\n")
        sb.append(CLOSING)
        sb.append("\n")
        sb.append(SIGNATURE)

        return sb
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}