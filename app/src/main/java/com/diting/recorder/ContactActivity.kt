package com.diting.recorder

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * （内容取自隐私政策）。
 * 布局规范：分区之间用一条细线隔开；分区内元素间距统一为 80px。
 */
class ContactActivity : AppCompatActivity() {

    private val itemGapPx = 80 // 需求：分区内元素间距 80px

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val toolbar = MaterialToolbar(this).apply { title = "联系我们" }
        applyBackIcon(toolbar)
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        content.addView(TextView(this).apply {
            text = "如果您对本应用或隐私政策有任何疑问，或有任何隐私相关的顾虑，欢迎通过以下方式联系我们："
            textSize = 14f
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(4), 0, dp(4), dp(16))
        })

        content.addView(buildContactItem(R.drawable.ic_tb_mail, "反馈邮箱", "shiyun_78@proton.me"))
        content.addView(buildContactItem(R.drawable.ic_tb_user, "酷安账号", "嘿鹰隼"))

        content.addView(TextView(this).apply {
            text = "点击条目可复制内容。"
            textSize = 12f
            setPadding(dp(4), dp(4), 0, 0)
            setTextColor(secondaryColor())
        })

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootColumn)
    }

    // ---------- 基础控件 ----------

    private fun buildContactItem(iconRes: Int, label: String, value: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.layoutParams = itemParams()
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(primaryColor())
        }
        root.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(secondaryColor())
        })
        col.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        root.isClickable = true
        root.isFocusable = true
        root.setOnClickListener { copyToClipboard(label, value) }
        return root
    }

    private fun copyToClipboard(label: String, value: String) {
        try {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
            Toast.makeText(this, "已复制：$value", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "复制失败：${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun itemParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = itemGapPx }
    }

    private fun applyBackIcon(toolbar: MaterialToolbar) {
        val back = ContextCompat.getDrawable(this, R.drawable.ic_tb_arrow_left)
        if (back != null) {
            back.setTint(secondaryColor())
            toolbar.setNavigationIcon(back)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun primaryColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLACK)

    private fun secondaryColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}