package com.diting.recorder

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.diting.recorder.util.NotificationPermission as UtilNotificationPermission
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * 权限配置 + 特殊配置（音频自检）。
 * 布局规范：分区之间用一条细线隔开；分区内元素间距统一为 80px。
 */
class PermissionSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQ_SHIZUKU = 2001
        private const val REQ_RECORD_AUDIO = 2002
        private const val REQ_PICK_DIR = 2003
        private const val REQ_NOTIFICATION = 2004
    }

    private val itemGapPx = 80 // 需求：分区内元素间距 80px

    private lateinit var shizukuStatus: TextView
    private lateinit var rootStatus: TextView
    private lateinit var recordAudioStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var notificationButton: MaterialButton
    private lateinit var saveLocationText: TextView
    private lateinit var audioCheckStatus: TextView
    private lateinit var audioCheckButton: MaterialButton
    private val uiHandler = Handler(Looper.getMainLooper())

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQ_SHIZUKU) {
            runOnUiThread { refreshShizukuStatus() }
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

        val toolbar = MaterialToolbar(this).apply { title = "权限项" }
        applyBackIcon(toolbar)
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }

        // ---- 权限配置 ----
        content.addView(sectionTitle("权限配置"))
        content.addView(buildShizukuItem())
        content.addView(buildRootItem())
        content.addView(buildRecordAudioItem())
        content.addView(buildNotificationItem())
        content.addView(buildSaveLocationItem())
        content.addView(caption("提示：系统声音捕获权限由 Root / Shizuku 授权后自动获得；录制文件由特权进程写入存储，无需额外申请存储权限。"))

        // ---- 分区细线 ----
        content.addView(dividerView(), dividerParams())

        // ---- 特殊配置 ----
        content.addView(sectionTitle("特殊配置"))
        content.addView(buildAudioCheckItem())

        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootColumn)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        refreshPermissionStatuses()
        refreshAudioCheckStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
        refreshAudioCheckStatus()
        if (::saveLocationText.isInitialized) renderSaveLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (t: Throwable) {
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            refreshRecordAudioStatus()
        }
        if (requestCode == REQ_NOTIFICATION) {
            refreshNotificationStatus()
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("通知权限已授权，可从通知栏停止录屏")
            } else {
                toast("通知权限被拒绝，可稍后在系统设置中开启")
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_DIR && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val path = treeUriToPath(uri)
            if (path != null) {
                RecorderSettings.setOutputDir(this, path)
                renderSaveLocation()
                toast("已选择目录：$path")
            } else {
                toast("无法解析所选文件夹，请换一个位置再试")
            }
        }
    }

    // ---------- 权限配置 ----------

    private fun buildShizukuItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_shield_lock, "Shizuku 授权")
        shizukuStatus = TextView(this).apply { textSize = 14f }
        item.content.addView(shizukuStatus)

        val button = actionButton("请求授权")
        button.setOnClickListener {
            try {
                if (!Shizuku.pingBinder()) {
                    toast("Shizuku 未运行：请先启动 Shizuku 应用")
                } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    toast("Shizuku 已授权")
                } else {
                    Shizuku.requestPermission(REQ_SHIZUKU)
                }
            } catch (t: Throwable) {
                toast("请求失败：${t.message}")
            }
        }
        item.content.addView(button, actionParams())
        item.content.addView(caption("用于以 shell 身份启动录制与音频自检。"))
        return item.root
    }

    private fun buildRootItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_terminal, "Root 授权")
        rootStatus = TextView(this).apply {
            textSize = 14f
            text = "未检测。点击下方按钮申请（首次会弹出 Root 管理器确认框）。"
        }
        item.content.addView(rootStatus)

        val button = actionButton("申请 / 检测 Root")
        button.setOnClickListener {
            rootStatus.text = "检测中……（如弹出 Root 管理器，请点击允许）"
            thread {
                val ok = try {
                    RootShell.isRootAvailable()
                } catch (t: Throwable) {
                    false
                }
                runOnUiThread {
                    rootStatus.text = if (ok) {
                        "已获得 Root 权限 ✅"
                    } else {
                        "未获得 Root 权限（被拒绝或设备无 Root）❌"
                    }
                }
            }
        }
        item.content.addView(button, actionParams())
        item.content.addView(caption("用于以 root 身份启动录制与音频自检（未授权时自动回退 Shizuku）。"))
        return item.root
    }

    private fun buildRecordAudioItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_microphone, "录音权限（RECORD_AUDIO）")
        recordAudioStatus = TextView(this).apply { textSize = 14f }
        item.content.addView(recordAudioStatus)

        val button = actionButton("申请权限")
        button.setOnClickListener {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toast("录音权限已授权")
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
            }
        }
        item.content.addView(button, actionParams())
        item.content.addView(caption("用于麦克风录制与音频自检（麦克风部分）。"))
        return item.root
    }

    private fun buildNotificationItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_bell, "通知权限（POST_NOTIFICATIONS）")
        notificationStatus = TextView(this).apply { textSize = 14f }
        item.content.addView(notificationStatus)

        notificationButton = actionButton("申请权限")
        notificationButton.setOnClickListener {
            when {
                UtilNotificationPermission.isGranted(this) ->
                    toast("通知权限已授权")
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) ->
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
                else -> {
                    // 未申请过 / 已被永久拒绝：都尝试系统弹窗，失败或拒绝后引导设置页
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
                }
            }
        }
        item.content.addView(notificationButton, actionParams())

        val settingsBtn = actionButton("打开系统通知设置")
        settingsBtn.setOnClickListener { UtilNotificationPermission.openNotificationSettings(this) }
        item.content.addView(settingsBtn, actionParams())

        item.content.addView(caption("必须权限：录屏的「停止」按钮挂在常驻通知上，没有通知权限就无法在通知栏停止录屏（也无法从后台一键停止）。"))
        return item.root
    }

    private fun refreshNotificationStatus() {
        if (!::notificationStatus.isInitialized) return
        val granted = UtilNotificationPermission.isGranted(this)
        val base = UtilNotificationPermission.statusText(this)
        val channelOff = UtilNotificationPermission.isChannelDisabled(this, "basic_recording")
        notificationStatus.text = when {
            channelOff -> "渠道已关闭 ⚠️（请到系统设置打开「录屏控制」通知）"
            else -> base
        }
        notificationButton.isEnabled = !granted
        notificationButton.text = if (granted) "已授权" else "申请权限"
    }

    private fun buildSaveLocationItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_folder, "保存位置")
        saveLocationText = TextView(this).apply { textSize = 14f }
        item.content.addView(saveLocationText)
        renderSaveLocation()

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pick = actionButton("选择文件夹")
        pick.setOnClickListener { openTreePicker() }
        row.addView(pick, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12) })
        val reset = actionButton("恢复默认")
        reset.setOnClickListener {
            RecorderSettings.setOutputDir(this, RecorderConfig.OUTPUT_DIR)
            renderSaveLocation()
            toast("已恢复默认目录")
        }
        row.addView(reset)
        item.content.addView(row, actionParams())
        item.content.addView(caption("默认 /sdcard/Movies/Diting（相册可见）；点击「选择文件夹」调起系统选择页。"))
        return item.root
    }

    private fun renderSaveLocation() {
        if (!::saveLocationText.isInitialized) return
        saveLocationText.text = "当前：${RecorderSettings.getOutputDir(this)}"
    }

    @Suppress("DEPRECATION")
    private fun openTreePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
            startActivityForResult(intent, REQ_PICK_DIR)
        } catch (t: Throwable) {
            toast("无法打开系统选择器：${t.message}")
        }
    }

    private fun treeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            val parts = docId.split(":", limit = 2)
            if (parts.size != 2) return null
            val volume = parts[0]
            val rel = parts[1]
            val root = when {
                volume.equals("primary", true) -> "/storage/emulated/0"
                volume.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) -> "/storage/$volume"
                else -> return null
            }
            if (rel.isBlank()) root else "$root/$rel"
        } catch (t: Throwable) {
            null
        }
    }

    // ---------- 特殊配置：音频自检 ----------

    private fun buildAudioCheckItem(): LinearLayout {
        val item = newItem(R.drawable.ic_tb_ear, "音频自检")
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val options = listOf(
            RecorderSettings.AUDIO_CHECK_AUTO to "自动",
            RecorderSettings.AUDIO_CHECK_MANUAL to "手动",
            RecorderSettings.AUDIO_CHECK_OFF to "关"
        )
        val current = RecorderSettings.getAudioCheckMode(this)
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
            if (value != RecorderSettings.getAudioCheckMode(this)) {
                RecorderSettings.setAudioCheckMode(this, value)
                refreshAudioCheckButtonState()
            }
        }
        item.content.addView(group)
        item.content.addView(caption("自动（默认）：每次打开应用时静默检测一次（结果见下方）；手动：打开测试页实时测试；关：不进行检测。"))

        audioCheckButton = actionButton("打开测试页（麦克风 / 系统声音）")
        audioCheckButton.setOnClickListener {
            if (RecorderSettings.getAudioCheckMode(this) == RecorderSettings.AUDIO_CHECK_OFF) {
                toast("音频自检已关闭")
            } else {
                startActivity(Intent(this, AudioTestActivity::class.java))
            }
        }
        item.content.addView(audioCheckButton, actionParams())

        audioCheckStatus = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
        }
        item.content.addView(audioCheckStatus)
        refreshAudioCheckButtonState()
        return item.root
    }

    private fun refreshAudioCheckStatus() {
        if (!::audioCheckStatus.isInitialized) return
        val report = AudioSelfCheck.readReport(this)
        audioCheckStatus.text = if (report == null) {
            "尚无检测记录"
        } else {
            AudioSelfCheck.formatReport(report)
        }
        refreshAudioCheckButtonState()
    }

    private fun refreshAudioCheckButtonState() {
        if (!::audioCheckButton.isInitialized) return
        val off = RecorderSettings.getAudioCheckMode(this) == RecorderSettings.AUDIO_CHECK_OFF
        audioCheckButton.isEnabled = !off
    }

    // ---------- 权限状态刷新 ----------

    private fun refreshPermissionStatuses() {
        refreshShizukuStatus()
        refreshRecordAudioStatus()
        refreshNotificationStatus()
    }

    private fun refreshShizukuStatus() {
        if (!::shizukuStatus.isInitialized) return
        shizukuStatus.text = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku 未运行（请先启动 Shizuku 应用）"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "已授权 ✅"
                else -> "等待授权 ⚠️"
            }
        } catch (t: Throwable) {
            "状态获取失败：${t.message}"
        }
    }

    private fun refreshRecordAudioStatus() {
        if (!::recordAudioStatus.isInitialized) return
        val granted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        recordAudioStatus.text = if (granted) "已授权 ✅" else "未授权 ⚠️"
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

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(MaterialColors.getColor(this@PermissionSettingsActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
            setPadding(dp(4), dp(16), 0, dp(12))
        }
    }

    private fun dividerView(): View {
        return View(this).apply {
            setBackgroundColor(MaterialColors.getColor(this@PermissionSettingsActivity, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY))
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

    private fun actionButton(text: String): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
        }
    }

    private fun actionParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun primaryColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLACK)

    private fun secondaryColor(): Int =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}