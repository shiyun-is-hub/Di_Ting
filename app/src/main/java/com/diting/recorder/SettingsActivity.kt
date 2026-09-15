package com.diting.recorder

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * 主菜单：录制项 / 权限项 / 关于。
 * 布局规范：分区之间用一条细线隔开；分区内元素间距统一为 80px。
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCROLL_TO_PERMISSIONS = "scroll_to_permissions"
    }

    private val itemGapPx = 80 // 需求：分区内元素间距 80px

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 直接进入「权限项」页面
        if (intent.getBooleanExtra(EXTRA_SCROLL_TO_PERMISSIONS, false)) {
            startActivity(Intent(this, PermissionSettingsActivity::class.java))
            finish()
            return
        }

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

        val toolbar = MaterialToolbar(this).apply { title = "设置" }
        applyBackIcon(toolbar)
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        content.addView(buildNavItem(R.drawable.ic_tb_video, "录制项", "外观、画质、帧率、麦克风、系统声音") {
            startActivity(Intent(this, RecordingSettingsActivity::class.java))
        })
        content.addView(buildNavItem(R.drawable.ic_tb_shield_lock, "权限项", "Shizuku / Root / 录音权限、保存位置、音频自检") {
            startActivity(Intent(this, PermissionSettingsActivity::class.java))
        })
        content.addView(buildNavItem(R.drawable.ic_tb_info_circle, "关于", "联系我们、隐私政策") {
            startActivity(Intent(this, AboutActivity::class.java))
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