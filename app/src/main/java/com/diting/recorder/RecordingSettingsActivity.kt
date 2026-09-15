package com.diting.recorder

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 视觉配置 + 听觉配置。
 * 布局规范：分区之间用一条细线隔开；分区内元素间距统一为 80px。
 */
class RecordingSettingsActivity : AppCompatActivity() {

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

        val toolbar = MaterialToolbar(this).apply { title = "录制项" }
        applyBackIcon(toolbar)
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }

        // ---- 视觉配置 ----
        content.addView(sectionTitle("视觉配置"))
        content.addView(buildThemeItem())
        content.addView(buildQualityItem())
        content.addView(buildFpsItem())

        // ---- 分区细线 ----
        content.addView(dividerView(), dividerParams())

        // ---- 听觉配置 ----
        content.addView(sectionTitle("听觉配置"))
        content.addView(buildMicItem())
        content.addView(buildSysAudioItem())
        content.addView(buildSingleTrackItem())

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootColumn)
    }

    // ---------- 视觉配置 ----------

    private fun buildThemeItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_palette, "外观（光暗模式）")
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val options = listOf(
            RecorderSettings.THEME_AUTO to "跟随系统",
            RecorderSettings.THEME_LIGHT to "浅色",
            RecorderSettings.THEME_DARK to "深色"
        )
        val current = RecorderSettings.getThemeMode(this)
        val radios = ArrayList<RadioButton>()
        var checkedIndex = -1
        options.forEachIndexed { index, (value, label) ->
            val rb = makeRadio(label)
            rb.id = View.generateViewId()
            rb.tag = value
            group.addView(rb)
            radios.add(rb)
            if (value == current) checkedIndex = index
        }
        if (checkedIndex >= 0) group.check(radios[checkedIndex].id)
        group.setOnCheckedChangeListener { g, checkedId ->
            val rb = g.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            val value = rb.tag as? String ?: return@setOnCheckedChangeListener
            if (value != RecorderSettings.getThemeMode(this)) {
                RecorderSettings.setThemeMode(this, value)
                AppCompatDelegate.setDefaultNightMode(
                    when (value) {
                        RecorderSettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        RecorderSettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }
        item.content.addView(group)
        item.content.addView(caption("默认跟随系统；切换后立即生效。"))
        return item.root
    }

    private fun buildQualityItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_photo, "录制画质")
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val options = listOf(
            RecorderConfig.QUALITY_1080P to "1080p",
            RecorderConfig.QUALITY_720P to "720p",
            RecorderConfig.QUALITY_360P to "360p",
            RecorderConfig.QUALITY_SCREEN to "跟随屏幕"
        )
        val current = RecorderSettings.getQuality(this)
        val radios = ArrayList<RadioButton>()
        var checkedIndex = -1
        options.forEachIndexed { index, (value, label) ->
            val rb = makeRadio(label)
            rb.id = View.generateViewId()
            rb.tag = value
            group.addView(rb)
            radios.add(rb)
            if (value == current) checkedIndex = index
        }
        if (checkedIndex >= 0) group.check(radios[checkedIndex].id)
        group.setOnCheckedChangeListener { g, checkedId ->
            val rb = g.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            val value = rb.tag as? String ?: return@setOnCheckedChangeListener
            RecorderSettings.setQuality(this, value)
        }
        item.content.addView(group)
        item.content.addView(caption("默认 1080p；编码器不支持所选档位时会自动降档，保证能录到。"))
        return item.root
    }

    private fun buildFpsItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_gauge, "录制帧率")
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val options = listOf(60, 50, 40, 30)
        val current = RecorderSettings.getFps(this)
        val radios = ArrayList<RadioButton>()
        var checkedIndex = -1
        options.forEachIndexed { index, value ->
            val rb = makeRadio(value.toString())
            rb.id = View.generateViewId()
            rb.tag = value
            group.addView(rb)
            radios.add(rb)
            if (value == current) checkedIndex = index
        }
        if (checkedIndex >= 0) group.check(radios[checkedIndex].id)
        group.setOnCheckedChangeListener { g, checkedId ->
            val rb = g.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            val value = rb.tag as? Int ?: return@setOnCheckedChangeListener
            RecorderSettings.setFps(this, value)
        }
        item.content.addView(group)
        item.content.addView(caption("默认 60；帧率越高越流畅，文件体积也越大。"))
        return item.root
    }

    // ---------- 听觉配置 ----------

    private fun buildMicItem(): LinearLayout {
        return buildToggleItem(
            R.drawable.ic_tb_microphone,
            "麦克风录制",
            "录制麦克风声音；与系统声音同时开启时，输出为双音轨（系统声音 + 麦克风）。",
            RecorderSettings.isMicEnabled(this)
        ) { value -> RecorderSettings.setMicEnabled(this, value) }
    }

    private fun buildSysAudioItem(): LinearLayout {
        return buildToggleItem(
            R.drawable.ic_tb_volume,
            "系统声音录制",
            "录制系统播放声音；能否捕获取决于设备 / ROM 限制，可用「音频自检」验证。",
            RecorderSettings.isSysAudioEnabled(this)
        ) { value -> RecorderSettings.setSysAudioEnabled(this, value) }
    }

    private fun buildSingleTrackItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_adjustments, "混音单轨")
        item.content.addView(caption("把麦克风与系统声音混合为一条音轨 · 功能开发中……"))
        return item.root
    }

    // ---------- 基础控件 ----------

    private class Item(val root: LinearLayout, val content: LinearLayout)

    private fun newItem(iconRes: Int, title: String): Item {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.layoutParams = itemParams()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(primaryColor())
        }
        head.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        head.addView(
            TextView(this).apply {
                text = title
                textSize = 15f
                setTypeface(Typeface.DEFAULT_BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) }
        )
        root.addView(head)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), 0, 0, 0)
        }
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        return Item(root, content)
    }

    private fun buildToggleItem(iconRes: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
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
        val sw = MaterialSwitch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        root.addView(sw)
        return root
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(MaterialColors.getColor(this@RecordingSettingsActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
            setPadding(dp(4), dp(16), 0, dp(12))
        }
    }

    private fun dividerView(): View {
        return View(this).apply {
            setBackgroundColor(MaterialColors.getColor(this@RecordingSettingsActivity, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY))
        }
    }

    private fun dividerParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(8) }
    }

    private fun itemParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = itemGapPx }
    }

    private fun makeRadio(label: String): RadioButton {
        return RadioButton(this).apply {
            text = label
            textSize = 16f
            setPadding(0, dp(6), dp(12), dp(6))
        }
    }

    private fun caption(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(secondaryColor())
            setPadding(0, dp(10), 0, 0)
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