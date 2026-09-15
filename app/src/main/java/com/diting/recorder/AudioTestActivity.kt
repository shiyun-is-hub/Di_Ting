package com.diting.recorder

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * 类似游戏内的麦克风测试：实时显示麦克风 / 系统声音音量条。
 */
class AudioTestActivity : AppCompatActivity() {

    private lateinit var micBar: LinearProgressIndicator
    private lateinit var sysBar: LinearProgressIndicator
    private lateinit var micStateText: TextView
    private lateinit var sysStateText: TextView
    private lateinit var resultText: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private val uiHandler = Handler(Looper.getMainLooper())

    private var running = false
    private var pollCount = 0

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            pollOnce()
            uiHandler.postDelayed(this, 150)
        }
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

        val toolbar = MaterialToolbar(this).apply {
            title = "音频自检"
            setNavigationIcon(R.drawable.ic_tb_arrow_left)
            navigationIcon?.setTint(MaterialColors.getColor(this@AudioTestActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY))
            setNavigationOnClickListener { finish() }
        }
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }

        val card = MaterialCardView(this).apply { radius = dp(16).toFloat() }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        card.addView(col)

        col.addView(TextView(this).apply {
            text = "操作说明"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(caption("对着麦克风说话，并播放一段音乐 / 视频；观察下面两条音量条是否跳动。"))
        col.addView(caption("就像游戏里的麦克风测试一样；检测会短暂占用麦克风与系统声音采集，属正常现象。"))

        col.addView(TextView(this).apply {
            text = "麦克风"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(6))
        })
        micBar = LinearProgressIndicator(this).apply { max = 100 }
        col.addView(micBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        micStateText = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary())
            text = "未开始"
            setPadding(0, dp(6), 0, 0)
        }
        col.addView(micStateText)

        col.addView(TextView(this).apply {
            text = "系统声音"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(6))
        })
        sysBar = LinearProgressIndicator(this).apply { max = 100 }
        col.addView(sysBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        sysStateText = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary())
            text = "未开始"
            setPadding(0, dp(6), 0, 0)
        }
        col.addView(sysStateText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = MaterialButton(this).apply {
            text = "开始检测"
            isAllCaps = false
        }
        startButton.setOnClickListener { startTest() }
        row.addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) })

        stopButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "停止"
            isAllCaps = false
            isEnabled = false
        }
        stopButton.setOnClickListener {
            AudioSelfCheck.requestStop(this)
            toast("正在停止…")
        }
        row.addView(stopButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })

        resultText = TextView(this).apply {
            textSize = 13f
            setTextColor(secondary())
            setPadding(0, dp(16), 0, 0)
        }
        col.addView(resultText)
        refreshLastResult()

        content.addView(card)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootColumn)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(pollRunnable)
        if (running) {
            running = false
            AudioSelfCheck.requestStop(this)
        }
    }

    private fun startTest() {
        if (running) return
        val started = try {
            AudioSelfCheck.launchTest(this)
        } catch (t: Throwable) {
            false
        }
        if (!started) {
            toast("无法启动检测：请先授予 Shizuku 或 Root 权限")
            return
        }
        running = true
        pollCount = 0
        startButton.isEnabled = false
        stopButton.isEnabled = true
        micStateText.text = "等待数据…"
        sysStateText.text = "等待数据…"
        resultText.text = "检测中……（对着麦克风说话 / 播放音乐试试）"
        uiHandler.postDelayed(pollRunnable, 300)
    }

    private fun pollOnce() {
        pollCount++
        if (pollCount > 700) {
            finishTest()
            return
        }
        val lv = AudioSelfCheck.readLevels(this)
        if (lv != null) {
            micBar.setProgressCompat(mapLevel(lv.micRms), false)
            sysBar.setProgressCompat(mapLevel(lv.sysRms), false)
            micStateText.text = describeState(lv.micState, lv.micBytes)
            sysStateText.text = describeState(lv.sysState, lv.sysBytes)
            if (lv.done) {
                finishTest()
            }
        }
    }

    private fun finishTest() {
        running = false
        startButton.isEnabled = true
        stopButton.isEnabled = false
        uiHandler.removeCallbacks(pollRunnable)
        micBar.setProgressCompat(0, false)
        sysBar.setProgressCompat(0, false)
        val report = AudioSelfCheck.readReport(this)
        resultText.text = if (report == null) "检测结束（未找到结果文件）" else AudioSelfCheck.formatReport(report)
    }

    private fun describeState(state: String, bytes: Long): String {
        return when {
            state == "ok" -> "运行中：已捕获 " + (bytes / 1024) + " KB"
            state.startsWith("fail:") -> "不可用：" + state.removePrefix("fail:").take(80)
            state == "starting" || state == "unknown" -> "初始化中…"
            else -> state
        }
    }

    private fun mapLevel(rms: Double): Int {
        if (rms <= 1e-8) return 0
        val db = 20 * Math.log10(rms)
        val v = ((db + 60) / 60 * 100).toInt()
        return v.coerceIn(0, 100)
    }

    private fun refreshLastResult() {
        val report = AudioSelfCheck.readReport(this)
        resultText.text = if (report == null) "尚无检测记录" else AudioSelfCheck.formatReport(report)
    }

    private fun secondary(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

    private fun caption(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(secondary())
            setPadding(0, dp(8), 0, 0)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}