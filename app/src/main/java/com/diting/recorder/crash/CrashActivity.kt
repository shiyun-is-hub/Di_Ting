package com.diting.recorder.crash

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.diting.recorder.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/**
 * - 顶部工具栏：标题 + 返回（关闭应用）
 * - 中部：ScrollView + TextView 展示完整崩溃日志
 * - 底部：「复制日志」「发送邮件」两个按钮
 *
 * 该页面不在最近任务列表中显示（manifest: excludeFromRecents + noHistory）
 */
class CrashActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_TEXT = "log_text"
        private const val MAIL_TO = "shiyun_78@proton.me"
        private const val MAIL_SUBJECT = "谛听录屏崩溃日志"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 优先使用 Intent 带入的日志，其次读取本地崩溃文件
        val logText = intent.getStringExtra(EXTRA_LOG_TEXT)
            ?: CrashHandler.readLog(this)
            ?: "（未读取到崩溃日志）"

        val rootColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // [新增] Android 15(targetSdk 35) edge-to-edge 适配：根布局避开系统栏
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootColumn) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val toolbar = MaterialToolbar(this).apply {
            title = "应用遇到了问题"
            setNavigationIcon(R.drawable.ic_tb_arrow_left)
            navigationIcon?.setTint(
                MaterialColors.getColor(
                    this@CrashActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.GRAY
                )
            )
            setNavigationOnClickListener { finishAndExit() }
        }
        rootColumn.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 提示文案
        rootColumn.addView(
            TextView(this).apply {
                text = "很抱歉，应用发生了崩溃。以下日志可以帮助我们定位问题，请反馈给开发者。"
                textSize = 13f
                setPadding(dp(16), dp(12), dp(16), dp(4))
                setTextColor(
                    MaterialColors.getColor(
                        this@CrashActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        Color.GRAY
                    )
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // 日志展示区：ScrollView + TextView
        val logView = TextView(this).apply {
            text = logText
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(logView)
        rootColumn.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // 底部按钮区
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        val copyButton = MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "复制日志"
            isAllCaps = false
            setOnClickListener { copyLog(logText) }
        }
        bottom.addView(
            copyButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = dp(12) }
        )

        val mailButton = MaterialButton(this).apply {
            text = "发送邮件"
            isAllCaps = false
            setOnClickListener { sendMail(logText) }
        }
        bottom.addView(
            mailButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        rootColumn.addView(
            bottom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(rootColumn)
    }

    /** 复制日志到剪贴板 */
    private fun copyLog(logText: String) {
        try {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("谛听录屏崩溃日志", logText))
            Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "复制失败：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 跳转系统邮件客户端 */
    private fun sendMail(logText: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_TO))
                putExtra(Intent.EXTRA_SUBJECT, MAIL_SUBJECT)
                putExtra(Intent.EXTRA_TEXT, logText)
            }
            startActivity(Intent.createChooser(intent, "发送崩溃日志"))
        } catch (t: ActivityNotFoundException) {
            Toast.makeText(this, "未找到邮件客户端，可先「复制日志」再手动发送", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "发送失败：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finishAndExit()
    }

    /** 关闭页面（不回退到崩溃前的界面，直接退出应用） */
    private fun finishAndExit() {
        try {
            finishAffinity()
        } catch (t: Throwable) {
            finish()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}