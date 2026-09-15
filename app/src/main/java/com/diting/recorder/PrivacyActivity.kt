package com.diting.recorder

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/**
 * 首次启动入口：
 * - 需滑动阅读至底部，并等待 5 秒后，「我已知晓」才可点击
 * - 「不同意（退出应用）」随时可点
 */
class PrivacyActivity : AppCompatActivity() {

    companion object {
        private const val WAIT_SECONDS = 5

        val POLICY_TEXT = """谛听录屏（Diting Recorder）隐私政策

更新日期：2026年9月12日
生效日期：2026年9月12日
适用版本：v1.0.0 及以上

一、前言

谛听录屏（以下简称“本应用”）是一款面向 Android 用户的本地录屏工具。

本应用的设计目标是：

• 本地处理
• 最小权限
• 用户控制
• 不依赖网络服务
• 尽可能保持功能和数据处理过程可审计

本应用不会以广告、用户画像、行为分析或数据收集为目的处理用户信息。

请在使用本应用前阅读并理解本隐私政策。

二、我们收集哪些信息

本应用不会主动收集、上传或向开发者提供以下信息：

• 姓名、电话号码、电子邮件地址等身份信息
• 位置信息
• 联系人及短信信息
• 设备标识符
• 剪贴板内容
• 浏览记录
• 录屏内容
• 录音内容
• 照片及其他媒体内容
• 应用使用统计信息
• 广告标识符
• 用于用户画像的数据

本应用没有用于广告、统计、分析或用户画像的第三方 SDK。

三、网络访问

本应用的正式版本不声明：

android.permission.INTERNET

因此，本应用自身不会通过 Android 普通网络 API 主动建立互联网连接，也不会将录屏文件、录音、应用配置或日志上传至开发者服务器。

本应用不提供云同步、云备份或远程上传功能。

需要特别说明的是：

“未声明 INTERNET 权限”并不意味着 Android 系统中不存在任何其他组件可能产生网络通信。例如，用户设备上的操作系统、应用商店、Shizuku、Root 管理器或其他第三方应用可以按照其自身的权限和功能进行网络通信。

这些通信不属于本应用主动进行的数据上传。

此外，开发本应用时使用的 Gradle、Android SDK、Maven 仓库等构建工具可能需要网络访问以下载开发依赖。该网络访问发生在应用构建阶段，并不代表已安装的应用具有网络访问能力。

四、录屏与音频数据

本应用的核心功能是录制用户主动选择的屏幕内容，并可根据用户设置录制音频。

录制内容仅在用户设备本地进行处理和保存。

录屏内容可能包含：

• 屏幕画面
• 应用界面
• 系统声音
• 麦克风声音

具体内容取决于用户选择的录制模式和音频设置。

本应用不会主动读取与录屏功能无关的其他应用数据。

请注意：用户自行录制的内容可能包含其他人的个人信息、通信内容、账号信息或其他敏感信息。用户应自行确认录制行为及录制内容符合所在地法律法规以及相关服务的使用规则。

五、权限说明

本应用遵循最小权限原则。不同 Android 版本、设备制造商和录制模式可能导致实际使用的权限有所不同。

1. RECORD_AUDIO

用于：

• 麦克风录音
• 音频功能测试

只有在用户启用麦克风录音或相关测试功能时才会使用麦克风。

2. POST_NOTIFICATIONS

用于 Android 13 及以上系统的录屏前台服务通知。

通知用于显示录屏状态以及提供停止录制等控制操作。

3. FOREGROUND_SERVICE

用于运行录屏前台服务。

4. FOREGROUND_SERVICE_MEDIA_PROJECTION

用于 Android 系统要求的媒体投影前台服务类型。

5. FOREGROUND_SERVICE_MICROPHONE

当录制麦克风声音时，用于符合 Android 对麦克风前台服务的要求。

6. MODIFY_AUDIO_ROUTING

该权限属于 Android 系统级音频能力。

本应用声明该权限主要用于支持特定设备、特定系统环境下的系统音频录制能力。

是否能够实际使用该能力取决于 Android 版本、设备制造商、系统权限策略以及当前运行模式。

声明该权限并不代表普通应用自动获得对应的系统级权限。

7. Shizuku 权限

本应用可以使用 Shizuku 为用户提供增强录屏能力。

Shizuku 权限由用户通过 Shizuku 管理。

获得 Shizuku 权限后，本应用仅将其用于录屏相关功能，例如调用系统底层显示、音频或媒体接口。

本应用不会以获取 Shizuku 权限为由：

• 修改用户系统设置
• 读取与录屏无关的其他应用数据
• 安装未知应用
• 上传用户数据
• 执行与录屏功能无关的操作

用户可以通过 Shizuku 管理器撤销本应用授权。

8. Root 权限

在支持 Root 模式的环境中，本应用可能请求用户授予 Root 权限。

Root 权限属于设备上的高权限能力。

如果用户授权 Root，本应用可能使用 Root 权限执行录屏所必需的系统级操作。

Root 权限并不会因为本隐私政策而自动获得，是否授予 Root 权限完全由用户控制。

用户应确认自己信任当前安装的应用版本后再授予 Root 权限。

六、录制文件与本地存储

录制文件默认保存在设备本地存储中，例如：

/sdcard/Movies/Diting/

具体位置可能根据应用版本、Android 版本及用户设置发生变化。

本应用不会自动将录制文件上传到互联网或第三方服务器。

用户可以通过系统文件管理器、相册或应用提供的相关功能管理和删除录制文件。

录制文件的实际删除状态还可能受到 Android 媒体库、文件系统以及系统缓存机制影响。

七、应用配置与日志

本应用可能在设备本地保存必要的应用配置，例如：

• 录制分辨率
• 帧率
• 比特率
• 音频设置
• 保存位置
• 应用运行状态

本应用也可能生成用于本地排障的运行日志。

这些配置和日志不会被本应用主动上传到开发者服务器。

如果用户主动将日志、录制文件或其他文件分享给开发者，则该数据将由用户主动提供。

八、第三方组件

本应用使用部分开源软件及 Android 官方组件，包括但不限于：

• Shizuku
• AndroidX
• Material Components
• Kotlin
• Kotlin Coroutines

这些组件用于实现 Android 应用界面、权限管理以及录屏相关功能。

具体依赖及其版本以本应用源码中的 Gradle 配置为准。

本应用基于 ZeroRecorder 开源项目进行二次开发。

ZeroRecorder 使用 Apache License 2.0 授权。本项目保留相应许可证及版权声明。

九、数据共享与披露

本应用不会将用户数据出售、出租、交换或用于商业广告。

在正常使用过程中，本应用不会主动向开发者或第三方提供：

• 录屏文件
• 音频文件
• 应用配置
• 设备信息
• 使用统计
• 日志数据

如果用户主动通过电子邮件、GitHub、酷安或其他渠道联系开发者并附带日志、截图、录屏或其他资料，则这些资料属于用户主动提交的信息。

开发者仅会在处理用户反馈、错误报告或技术问题所必要的范围内使用这些资料。

十、数据删除

用户可以通过以下方式删除本地数据：

• 删除录制的视频文件
• 删除应用产生的本地日志
• 清除应用数据
• 卸载本应用

由于录制文件可能由 Android 媒体库或其他系统组件管理，用户删除文件后，其最终清理行为可能受到 Android 系统机制影响。

十一、儿童与未成年人

本应用不是面向儿童设计的服务，也不会主动收集儿童个人信息。

如果未成年人使用本应用进行录屏或录音，建议在监护人的指导下使用。

用户应特别注意录制他人、未成年人或包含个人信息的内容可能涉及隐私及法律问题。

十二、信息安全

本应用采用本地处理和最小权限设计，以减少不必要的数据暴露。

但是，任何软件都无法保证在所有设备、Android 版本、Root 环境、第三方系统组件以及恶意软件存在的情况下实现绝对安全。

特别是：

• Root 会显著提高应用能够执行的系统权限范围
• Shizuku 授权会使应用获得超出普通应用的系统访问能力
• 被用户主动分享的录制文件可能包含敏感信息
• 如果设备本身存在恶意软件或系统被修改，本应用无法保证设备整体安全

因此，用户应仅向自己信任的版本授予 Root 或 Shizuku 权限。

十三、兼容性说明

本应用部分功能依赖 Android 的系统接口、隐藏接口以及设备制造商提供的系统实现。

不同 Android 版本及厂商 ROM 可能导致：

• 系统声音无法录制
• 麦克风无法使用
• 录屏失败
• 部分应用拒绝被录制
• 视频编码失败
• 录制画面异常
• 高权限功能不可用

因此，本应用不保证在所有 Android 设备上提供完全一致的功能。

十四、免责声明

本应用按“现状”提供。

开发者不保证：

• 在所有设备上均能正常录制
• 所有录制内容均能成功保存
• 系统声音在所有设备上均可录制
• 录制过程绝不会发生异常
• 录制文件在任何情况下均不会损坏

用户应在重要录制任务前自行测试。

用户应遵守所在地法律法规以及相关平台的使用规则，不得利用本应用侵犯他人隐私、著作权或其他合法权益。

因用户使用本应用产生的录制行为及录制内容所引起的法律责任，由用户依法自行承担。

十五、隐私政策更新

本隐私政策可能因应用功能、权限、法律法规或技术架构变化而更新。

由于本应用不依赖网络推送，因此不会通过应用内网络推送隐私政策更新通知。

更新后的政策将随应用源码或正式发布版本一同提供，并标注更新日期。

十六、联系我们

如果您对本隐私政策、隐私保护或应用功能存在疑问，可以通过以下方式联系开发者：

反馈邮箱：
shiyun_78@proton.me

酷安：
嘿鹰隼

开源项目及源码：
请以本应用实际发布页面所提供的官方源码仓库为准。

最后更新：
2026年9月12日"""
    }

    private var bottomReached = false
    private var secondsLeft = WAIT_SECONDS
    private lateinit var agreeButton: MaterialButton
    private lateinit var hintText: TextView
    private val uiHandler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (secondsLeft > 0) {
                secondsLeft--
                updateHint()
                uiHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已同意过 → 直接进入主界面
        if (RecorderSettings.isPrivacyAgreed(this)) {
            startActivity(Intent(this, MainActivity::class.java))
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

        val toolbar = MaterialToolbar(this).apply { title = "隐私政策" }
        rootColumn.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val policyView = TextView(this).apply {
            text = POLICY_TEXT
            textSize = 13.5f
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setTextIsSelectable(true)
        }
        scroll.addView(policyView)

        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val child = scroll.getChildAt(0)
            if (child != null && scrollY + scroll.height >= child.height - dp(24)) {
                if (!bottomReached) {
                    bottomReached = true
                    updateHint()
                }
            }
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        hintText = TextView(this).apply {
            textSize = 13f
            setTextColor(MaterialColors.getColor(this@PrivacyActivity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
        }
        bottom.addView(hintText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val disagreeButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "不同意（退出应用）"
            isAllCaps = false
        }
        disagreeButton.setOnClickListener { exitApp() }
        row.addView(disagreeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) })

        agreeButton = MaterialButton(this).apply {
            text = "我已知晓"
            isAllCaps = false
            isEnabled = false
        }
        agreeButton.setOnClickListener {
            RecorderSettings.setPrivacyAgreed(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        row.addView(agreeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        bottom.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        rootColumn.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        rootColumn.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(rootColumn)

        // 如果内容不足一屏，直接视为"已到底部"
        scroll.post {
            val child = scroll.getChildAt(0)
            if (child != null && child.height <= scroll.height + dp(4)) {
                bottomReached = true
            }
            updateHint()
        }

        uiHandler.postDelayed(timerRunnable, 1000)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        exitApp()
    }

    private fun exitApp() {
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun refreshAgree() {
        if (!::agreeButton.isInitialized) return
        agreeButton.isEnabled = bottomReached && secondsLeft <= 0
    }

    private fun updateHint() {
        val bottomPart = if (bottomReached) "已阅读到底部 ✓" else "请滑动阅读至底部"
        hintText.text = if (secondsLeft > 0) {
            "翻到底部并等待 $secondsLeft 秒后可确认 · $bottomPart"
        } else {
            "等待完成 ✓ · $bottomPart"
        }
        refreshAgree()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}