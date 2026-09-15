package com.diting.recorder

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * 展示 LICENSE.html 的内容。策略：
 * 1. 优先用系统浏览器打开在线地址（与官网/仓库同源，便于统一维护）
 * 2. 无法打开浏览器时，回退到内置 WebView 显示打包在 assets 中的离线副本
 *
 * 说明：若要更新许可内容，只需替换 assets/LICENSE.html 并同步更新在线地址即可。
 */
class LicenseActivity : AppCompatActivity() {

    companion object {
        /**
         * 在线许可页地址（部署后可指向你的网站 / GitHub Pages）。
         * 当前为占位地址，发布前请替换为实际地址。
         */
        private const val ONLINE_URL = "https://shiyun-is-hub.github.io/Diting/LICENSE.html"

        /** 内置离线副本（assets） */
        private const val ASSET_PATH = "file:///android_asset/LICENSE.html"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootColumn = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        // [新增] Android 15(targetSdk 35) edge-to-edge 适配：根布局避开系统栏
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootColumn) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val toolbar = MaterialToolbar(this).apply { title = "开源许可" }
        val back = ContextCompat.getDrawable(this, R.drawable.ic_tb_arrow_left)
        if (back != null) {
            back.setTint(
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    android.graphics.Color.GRAY
                )
            )
            toolbar.navigationIcon = back
        }
        toolbar.setNavigationOnClickListener { finish() }
        rootColumn.addView(
            toolbar,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val web = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            setBackgroundColor(
                MaterialColors.getColor(
                    this@LicenseActivity,
                    com.google.android.material.R.attr.colorSurface,
                    android.graphics.Color.BLACK
                )
            )
        }
        rootColumn.addView(
            web,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(rootColumn)

        // 直接展示内置离线副本，保证任何情况下都能看到内容
        web.loadUrl(ASSET_PATH)
    }

    /** 供 AboutActivity 调用：优先浏览器打开在线页，失败则跳本页离线版 */
    fun openInBrowser(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ONLINE_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }
}