package com.diting.recorder

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * 联系我们 + 隐私政策。
 * 布局规范：分区之间用一条细线隔开；分区内元素间距统一为 80px。
 */
class AboutActivity : AppCompatActivity() {

    private val itemGapPx = 80 // 需求：分区内元素间距 80px

    companion object {
        /** 在线开源许可页地址（发布前替换为实际地址，与 assets/LICENSE.html 内容一致） */
        private const val ONLINE_LICENSE_URL = "https://shiyun-is-hub.github.io/Diting/LICENSE.html"
    }

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

        val toolbar = MaterialToolbar(this).apply { title = "关于" }
        applyBackIcon(toolbar)
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        // 应用信息头
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(16))
        }
        header.addView(TextView(this).apply {
            text = "谛听"
            textSize = 20f
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        header.addView(TextView(this).apply {
            text = "版本 ${appVersion()} · 本地录屏工具"
            textSize = 13f
            setPadding(0, dp(6), 0, 0)
            setTextColor(secondaryColor())
        })
        content.addView(header)

        // 细线
        content.addView(dividerView(), dividerParams())

        content.addView(buildNavItem(R.drawable.ic_tb_mail, "联系我们", "反馈邮箱与酷安账号") {
            startActivity(Intent(this, ContactActivity::class.java))
        })
        content.addView(buildNavItem(R.drawable.ic_tb_file_text, "隐私政策", "查看完整隐私政策") {
            startActivity(Intent(this, PrivacyViewerActivity::class.java))
        })
        content.addView(buildNavItem(R.drawable.ic_tb_code, "开源许可", "开源协议、修改声明与第三方组件") {
            openLicense()
        })

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootColumn)
    }

    // ---------- 基础控件 ----------

    private fun buildNavItem(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
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
            text = title
            textSize = 15f
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        col.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
            setTextColor(secondaryColor())
        })
        root.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        val chev = ImageView(this).apply {
            setImageResource(R.drawable.ic_tb_chevron_right)
            imageTintList = ColorStateList.valueOf(secondaryColor())
        }
        root.addView(chev, LinearLayout.LayoutParams(dp(20), dp(20)))
        root.isClickable = true
        root.isFocusable = true
        root.setOnClickListener { onClick() }
        return root
    }

    /**
     * 打开开源许可：优先系统浏览器（在线页），
     * 若无浏览器可用则回退到应用内离线版（assets/LICENSE.html）。
     */
    private fun openLicense() {
        val online = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ONLINE_LICENSE_URL))
        try {
            startActivity(online)
        } catch (t: Throwable) {
            startActivity(Intent(this, LicenseActivity::class.java))
        }
    }

    private fun dividerView(): View {
        return View(this).apply {
            setBackgroundColor(MaterialColors.getColor(this@AboutActivity, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY))
        }
    }

    private fun dividerParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(4) }
    }

    private fun itemParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = itemGapPx }
    }

    private fun appVersion(): String {
        return try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            pi.versionName ?: "1.0.0"
        } catch (t: Throwable) {
            "1.0.0"
        }
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