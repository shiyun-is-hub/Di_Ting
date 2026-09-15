package com.diting.recorder

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * 随时可退出（与首次启动的同意页不同，无门槛）。
 */
class PrivacyViewerActivity : AppCompatActivity() {

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

        val toolbar = MaterialToolbar(this).apply {
            title = "隐私政策"
            setNavigationIcon(R.drawable.ic_tb_arrow_left)
            navigationIcon?.setTint(MaterialColors.getColor(this@PrivacyViewerActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY))
            setNavigationOnClickListener { finish() }
        }
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val policyView = TextView(this).apply {
            text = PrivacyActivity.POLICY_TEXT
            textSize = 13.5f
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setTextIsSelectable(true)
        }
        scroll.addView(policyView)

        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootColumn)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}