package com.diting.recorder

import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.diting.recorder.util.NotificationPermission
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * 原生 Material Design 风格。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var statusCard: MaterialCardView
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var configCaption: TextView
    private lateinit var notifCaption: TextView
    private var settingsPageTitle: TextView? = null
    private val SHIZUKU_CODE = 1001
    private val REQ_BASIC_PROJECTION = 3101
    private val REQ_BASIC_MIC = 3102
    private val REQ_BASIC_NOTIF = 3103
    private var pendingBasicFlowAfterNotif = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_CODE) {
            runOnUiThread { updateUI() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.FrameLayout(this)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.clipToPadding = false
        scroll.setPadding(0, 0, 0, dp(72))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(80), dp(24), dp(32))
        }

        val titleText = TextView(this).apply {
            text = "谛听"
            textSize = 34f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }
        col.addView(titleText)

        val subTitle = TextView(this).apply {
            text = "后台录屏工具"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(secondaryColor())
        }
        col.addView(subTitle, topParams(dp(6)))

        statusCard = MaterialCardView(this).apply { radius = dp(20).toFloat() }
        val cardRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        statusIcon = ImageView(this).apply {
            setPadding(0, 0, dp(8), 0)
        }
        statusText = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            text = "检查中…"
        }
        cardRow.addView(statusIcon)
        cardRow.addView(statusText)
        statusCard.addView(cardRow)
        col.addView(statusCard, topParams(dp(36)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        startButton = MaterialButton(this).apply {
            text = "开始录制"
            isAllCaps = false
            setIconResource(R.drawable.ic_tb_player_play)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8)
        }
        startButton.setOnClickListener { handleStartClick() }
        row.addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) })

        stopButton = MaterialButton(this).apply {
            text = "停止录制"
            isAllCaps = false
            isEnabled = false
            setIconResource(R.drawable.ic_tb_player_stop)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8)
        }
        stopButton.setOnClickListener {
            thread {
                RecorderLauncher.forceStop()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "已发送停止指令", Toast.LENGTH_SHORT).show()
                    updateUI()
                }
            }
        }
        row.addView(stopButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row, topParams(dp(36), LinearLayout.LayoutParams.MATCH_PARENT))

        val basicButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "基础录屏（免 Root / 免 Shizuku）"
                isAllCaps = false
                setIconResource(R.drawable.ic_tb_player_record)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = dp(8)
            }
            basicButton.setOnClickListener { startBasicRecordingFlow() }
            col.addView(basicButton, topParams(dp(12), LinearLayout.LayoutParams.MATCH_PARENT))

            configCaption = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(secondaryColor())
        }
        notifCaption = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FB8C00"))
        }
        col.addView(notifCaption, topParams(dp(12)))

        col.addView(configCaption, topParams(dp(16)))
        renderConfigCaption()

        scroll.addView(col)

        // 滑动容器：主页 ⇄ 设置
        val pager = com.diting.recorder.ui.SwipePager(this)
        pager.setPages(listOf(scroll, buildSettingsPage()))
        root.addView(pager, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 顶栏（右上角公告入口，仅主页显示）
        val topBar = buildTopBar()
        root.addView(topBar, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            dp(56),
            Gravity.TOP
        ))

        // 底栏（主页 / 设置，常驻）
        root.addView(buildBottomBar(pager), android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            dp(72),
            Gravity.BOTTOM
        ))

        // [新增] 系统栏适配：Android 15(targetSdk 35) 强制 edge-to-edge，
        // 必须手动把顶/底栏推开状态栏与导航栏，否则内容会被系统栏遮住。
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            // 顶栏下移状态栏高度
            (topBar.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                topMargin = bars.top
                height = dp(56) + bars.top
            }
            // 底栏上移导航栏高度
            (root.getChildAt(2).layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom
                height = dp(72) + bars.bottom
            }
            // 主页内容顶部留出 状态栏 + 顶栏 的高度
            col.setPadding(dp(24), dp(80) + bars.top, dp(24), dp(32))
            // 设置页顶部留白
            settingsPageTitle?.setPadding(dp(24), dp(24) + bars.top, dp(24), dp(8))
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)

        // 公告入口只在主页显示
        topBar.visibility = android.view.View.VISIBLE
        pager.addOnPageChanged { page ->
            topBar.visibility = if (page == 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
        }

        setContentView(root)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        // 自动打开设置页并跳转到"权限配置"分区
        if (!RecorderSettings.isFirstLaunchDone(this)) {
            RecorderSettings.setFirstLaunchDone(this)
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_SCROLL_TO_PERMISSIONS, true)
            )
        }

        // 自动模式：每次打开应用（冷启动）时执行一次
        if (savedInstanceState == null &&
            RecorderSettings.getAudioCheckMode(this) == RecorderSettings.AUDIO_CHECK_AUTO
        ) {
            thread {
                try {
                    AudioSelfCheck.launch(this)
                } catch (t: Throwable) {
                    RecorderLog.w("ZR.AudioCheck", "auto check failed: ${t.message}")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderConfigCaption()
        renderNotificationCaption()
        updateUI()
    }

    private fun renderNotificationCaption() {
        if (!::notifCaption.isInitialized) return
        notifCaption.text = if (NotificationPermission.isNeeded(this)) {
            "⚠️ 通知权限未开启：录屏时将无法在通知栏停止（点此开启）"
        } else {
            ""
        }
        notifCaption.setOnClickListener {
            if (NotificationPermission.isNeeded(this)) {
                startActivity(
                    Intent(this, PermissionSettingsActivity::class.java)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun updateUI() {
        if (!Shizuku.pingBinder()) {
            setStatus(R.drawable.ic_tb_circle_x, "Shizuku 未运行，请先激活", colorError())
            startButton.isEnabled = false
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            setStatus(R.drawable.ic_tb_circle_check, "Shizuku 已授权，可以录制", Color.parseColor("#43A047"))
            startButton.isEnabled = true
            startButton.text = "开始录制"
            startButton.setIconResource(R.drawable.ic_tb_player_play)
            stopButton.isEnabled = true
        } else {
            setStatus(R.drawable.ic_tb_alert_triangle, "等待 Shizuku 授权", Color.parseColor("#FB8C00"))
            startButton.isEnabled = true
            startButton.text = "请求授权"
            startButton.setIconResource(R.drawable.ic_tb_shield_lock)
            stopButton.isEnabled = true
        }
    }

    private fun setStatus(iconRes: Int, text: String, color: Int) {
        statusIcon.setImageResource(iconRes)
        statusIcon.imageTintList = ColorStateList.valueOf(color)
        statusText.text = text
        statusText.setTextColor(color)
    }

    private fun handleStartClick() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
            return
        }

        val success = RecorderLauncher.startRecording(this)
        if (success) {
            Toast.makeText(this, "已在后台开始录制", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "启动失败，请检查环境", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderConfigCaption() {
        val quality = RecorderSettings.getQuality(this)
        val qualityLabel = if (quality == RecorderConfig.QUALITY_SCREEN) "跟随屏幕" else quality
        val fps = RecorderSettings.getFps(this)
        configCaption.text = "当前配置：$qualityLabel · ${fps}fps"
    }

    private fun secondaryColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

    private fun colorError(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, Color.RED)

    private fun topParams(top: Int, width: Int = LinearLayout.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = top }

    // ---------- 顶栏：右上角公告入口 ----------

    private fun buildTopBar(): android.view.View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(16), 0, dp(12), 0)
            // 跟随主题：浅色主题=浅色栏，深色主题=深色栏
            setBackgroundColor(barBackground())
            elevation = dp(3).toFloat()
        }

        val noticeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_tb_clipboard_text)
            imageTintList = ColorStateList.valueOf(primaryColor())
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = "更新公告"
            setOnTouchListener { v, e -> pressAnim(v, e, 0.85f) }
        }
        noticeBtn.setOnClickListener { UpdateNotice.show(this) }
        bar.addView(noticeBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

        val wrap = android.widget.FrameLayout(this)
        wrap.addView(bar, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        wrap.addView(android.view.View(this).apply {
            setBackgroundColor(dividerColor())
        }, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            dp(1),
            Gravity.BOTTOM
        ))
        return wrap
    }

    // ---------- 底栏：主页 / 设置（常驻） ----------

    private fun buildBottomBar(pager: com.diting.recorder.ui.SwipePager): android.view.View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(barBackground())
            elevation = dp(3).toFloat()
        }

        val homeBtn = buildBarItem(R.drawable.ic_tb_home, "主页", true)
        homeBtn.setOnClickListener {
            if (pager.getCurrentPage() == 0) {
                Toast.makeText(this, "已回到顶部", Toast.LENGTH_SHORT).show()
            } else {
                pager.goToPage(0)
            }
        }
        bar.addView(homeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val settingsBtn = buildBarItem(R.drawable.ic_tb_settings, "设置", false)
        settingsBtn.setOnClickListener { pager.goToPage(1) }
        bar.addView(settingsBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        // 页切换时同步高亮
        pager.addOnPageChanged { page ->
            setBarItemActive(homeBtn, page == 0)
            setBarItemActive(settingsBtn, page == 1)
        }

        val wrap = android.widget.FrameLayout(this)
        wrap.addView(bar, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        wrap.addView(android.view.View(this).apply {
            setBackgroundColor(dividerColor())
        }, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            dp(1),
            Gravity.TOP
        ))
        return wrap
    }

    /** 底栏单项：图标 + 文字。 */
    private fun buildBarItem(iconRes: Int, label: String, active: Boolean): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
        }
        val color = if (active) primaryColor() else secondaryColor()

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(color)
        }
        item.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))

        val text = TextView(this).apply {
            this.text = label
            textSize = 11f
            setTextColor(color)
            gravity = Gravity.CENTER
        }
        item.addView(text, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })

        item.setOnTouchListener { v, e -> pressAnim(v, e, 0.9f) }
        return item
    }

    /** 切换底栏单项的选中态。 */
    private fun setBarItemActive(item: LinearLayout, active: Boolean) {
        val color = if (active) primaryColor() else secondaryColor()
        (item.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(color)
        (item.getChildAt(1) as? TextView)?.setTextColor(color)
    }

    /** 通用按下缩放动画。 */
    private fun pressAnim(v: android.view.View, e: android.view.MotionEvent, scale: Float): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        return false
    }

    // ---------- 设置页（列表，二级菜单仍跳转 Activity） ----------

    private fun buildSettingsPage(): android.view.View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor())
        }

        val title = TextView(this).apply {
            text = "设置"
            textSize = 22f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(24), dp(24), dp(24), dp(8))
        }
        settingsPageTitle = title
        wrap.addView(title)

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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            clipToPadding = false
            setPadding(0, 0, 0, dp(72))
        }
        wrap.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        return wrap
    }

    /** 设置页列表项（图标 + 标题 + 副标题 + 箭头）。 */
    private fun buildNavItem(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) }

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
        root.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().alpha(0.6f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.animate().alpha(1f).setDuration(120).start()
            }
            false
        }
        return root
    }

    private fun surfaceColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.parseColor("#FF121212"))

    /** 顶/底栏背景：跟随主题（colorSurface，叠加轻微透明保留层次感）。 */
    private fun barBackground(): Int {
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        // 85% 不透明，保留一点"浮层"观感，但仍随主题变色
        val a = 0xE6 // ~229/255
        return (a shl 24) or (surface and 0x00FFFFFF)
    }

    /** 分隔线颜色：跟随主题。 */
    private fun dividerColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, Color.parseColor("#33000000"))

    private fun primaryColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#3B82F6"))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    // ---------- 基础录屏（MediaProjection，免 Root / 免 Shizuku） ----------

    private fun startBasicRecordingFlow() {
        if (com.diting.recorder.basic.BasicRecordService.isRunning) {
            Toast.makeText(this, "基础录屏已在进行中", Toast.LENGTH_SHORT).show()
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "基础录屏需要 Android 10 及以上系统", Toast.LENGTH_SHORT).show()
            return
        }

        // 麦克风权限（仅当开启麦克风录制时需要）
        if (RecorderSettings.isMicEnabled(this) && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_BASIC_MIC)
        }
        // 通知权限（Android 13+）：必需！没有它通知栏不会出现「停止」按钮。
        // 先申请，授权后再继续走录屏授权流程。
        if (NotificationPermission.isNeeded(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_BASIC_NOTIF)
            pendingBasicFlowAfterNotif = true
            return
        }

        proceedBasicCaptureFlow()
    }

    // 授权结果回调：如果是为了启动基础录屏而申请的，则继续流程
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_BASIC_NOTIF) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Toast.makeText(this, "未授予通知权限：录屏将无法在通知栏停止", Toast.LENGTH_LONG).show()
            }
            if (pendingBasicFlowAfterNotif) {
                pendingBasicFlowAfterNotif = false
                proceedBasicCaptureFlow()
            }
        }
    }

    private fun proceedBasicCaptureFlow() {
        val mgr = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        try {
            // Android 14+ 默认会弹出“单应用选择器”，不选应用则永远不返回；
            // 录屏软件应直接录“整个屏幕”，用 createConfigForDefaultDisplay 简化授权。
            val captureIntent = if (android.os.Build.VERSION.SDK_INT >= 34) {
                mgr.createScreenCaptureIntent(
                    android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                )
            } else {
                @Suppress("DEPRECATION")
                mgr.createScreenCaptureIntent()
            }
            @Suppress("DEPRECATION")
            startActivityForResult(captureIntent, REQ_BASIC_PROJECTION)
        } catch (t: Throwable) {
            Toast.makeText(this, "无法打开录屏授权：${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BASIC_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "已取消基础录屏", Toast.LENGTH_SHORT).show()
            return
        }

        val quality = RecorderSettings.getQuality(this)
        val fps = RecorderSettings.getFps(this)
        val mic = RecorderSettings.isMicEnabled(this)
        val sysAudio = RecorderSettings.isSysAudioEnabled(this)
        val outDir = RecorderSettings.getOutputDir(this)
        val baseName = "Rec_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())

        val serviceIntent = Intent(this, com.diting.recorder.basic.BasicRecordService::class.java)
            .setAction(com.diting.recorder.basic.BasicRecordService.ACTION_START)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_RESULT_CODE, resultCode)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_RESULT_DATA, data)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_MIC, mic)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_SYS_AUDIO, sysAudio)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_QUALITY, quality)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_FPS, fps)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_OUT_DIR, outDir)
            .putExtra(com.diting.recorder.basic.BasicRecordService.EXTRA_BASE_NAME, baseName)
        try {
            startForegroundService(serviceIntent)
            Toast.makeText(
                this,
                if (NotificationPermission.isGranted(this)) "基础录屏已启动，可在通知栏停止"
                else "基础录屏已启动（通知权限未开启，无法在通知栏停止）",
                Toast.LENGTH_LONG
            ).show()
            moveTaskToBack(true)
        } catch (t: Throwable) {
            Toast.makeText(this, "启动失败：${t.message}", Toast.LENGTH_LONG).show()
        }
    }
}