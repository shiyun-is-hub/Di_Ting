# 谛听 · Diting

> 一款专注于本地录制的 Android 屏幕录制工具 —— 录制在本机完成，不上传、不联网、不收集数据。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](#环境要求)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org/)

---

## 目录

- [简介](#简介)
- [功能特性](#功能特性)
- [两种录制模式](#两种录制模式)
- [界面预览](#界面预览)
- [环境要求](#环境要求)
- [构建与安装](#构建与安装)
- [项目结构](#项目结构)
- [输出文件](#输出文件)
- [常见问题](#常见问题)
- [隐私说明](#隐私说明)
- [基于上游项目的说明](#基于上游项目的说明)
- [开源许可](#开源许可)
- [联系我们](#联系我们)

---

## 简介

**谛听（Diting）** 是一款面向 Android 的**本地录屏工具**。它提供两种互补的录制方式：适合所有用户的「基础录屏」，以及面向高级用户的「增强录屏」。

所有录制过程均在设备本地完成，应用**不申请网络权限、不上传任何数据、不收集用户信息**。详见 [隐私说明](#隐私说明) 与应用内《隐私政策》。

---

## 功能特性

### 录制能力

- **两种录制模式**：基础录屏（免 Root / 免 Shizuku）与增强录屏（Shizuku / Root）
- **画质可选**：跟随屏幕 / 1080p / 720p 等
- **帧率可选**：30fps / 60fps
- **音频录制**：麦克风 + 系统内部声音（能力取决于设备与授权方式）
- **音频自检**：自动 / 手动检测麦克风与系统声音可用性

### 界面与体验

- **原生 Material 3 界面**：完整支持深色 / 浅色 / 跟随系统三态
- **动态取色**：Android 12+ 跟随系统壁纸主题
- **设置三层结构**：录制项 / 权限项 / 关于
- **统一图标体系**：基于 [Tabler Icons](https://tabler.io/icons)

### 稳定性与合规

- **全局崩溃捕获**：记录设备型号、系统版本、App 版本与完整堆栈，支持一键反馈
- **通知权限引导**：确保录屏可从通知栏一键停止
- **隐私政策门槛**：首次启动需完整阅读并同意后方可使用
- **开源许可页**：内置开源协议、修改声明与第三方组件说明

---

## 两种录制模式

### 🟢 基础录屏（推荐给所有用户）

基于系统标准的 `MediaProjection` 接口，**免 Root、免 Shizuku**。

| 项目 | 说明 |
|---|---|
| 使用前提 | 无（仅需在弹窗中授权一次） |
| 授权方式 | 系统标准的「整个屏幕」录屏授权弹窗 |
| 系统要求 | Android 10（API 29）及以上 |
| 系统声音 | 取决于厂商 ROM 对 `AudioPlaybackCapture` 的支持 |
| 适合人群 | 所有用户 |

**操作流程**：主界面 → 点「基础录屏」→ 系统弹窗点「开始」→ 通知栏出现「谛听录屏 · 正在录屏…」→ 点通知里的「停止」结束。

### 🔵 增强录屏（面向高级用户）

通过 [Shizuku](https://shizuku.rikka.app/) 以 shell 身份运行独立采集进程，可获得更强的系统声音捕获能力。

| 项目 | 说明 |
|---|---|
| 使用前提 | 已安装并激活 Shizuku（或设备已有 Root） |
| 授权方式 | Shizuku 授权 / Root 授权 |
| 系统要求 | Android 8.0（API 26）及以上 |
| 系统声音 | 成功率更高（仍受 ROM 策略影响） |
| 适合人群 | 需要稳定录制系统声音的高级用户 |

> 💡 增强录屏会使用应用内的悬浮窗控制菜单，支持录制状态显示、计时与一键停止。

---

## 界面预览

> 截图待补充（将随商店发布素材一并整理）

| 主界面 | 设置 | 关于 |
|:---:|:---:|:---:|
| 状态卡片 + 录制按钮 | 录制项 / 权限项 / 关于 | 联系我们 / 隐私政策 / 开源许可 |

---

## 环境要求

### 运行环境

- Android 8.0（API 26）及以上
- 基础录屏需要 Android 10（API 29）及以上
- 增强录屏需要安装 Shizuku（或设备已 Root）

### 构建环境

- **JDK**：17
- **Android SDK**：compileSdk 34，build-tools 34.0.4
- **Gradle**：9.3.1（项目自带 wrapper）
- **Kotlin**：1.9.22
- **Android Gradle Plugin**：8.13.2

---

## 构建与安装

### 使用 Gradle 构建

```bash
# 克隆仓库
git clone https://github.com/shiyun-is-hub/Di_Ting.git
cd Diting

# 配置签名（可选，见下方说明）
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填入你的密钥库信息

# 构建 release 版本
./gradlew assembleRelease

# 产物位置
# app/build/outputs/apk/release/app-release.apk
```

### 关于签名

- 项目支持从 `keystore.properties` 读取正式签名配置
- **该文件已加入 `.gitignore`，不会提交到仓库**
- 若缺少该文件，构建会自动回退为 debug 签名（仅用于本地测试）

`keystore.properties` 示例：

```properties
storeFile=diting-release.jks
storePassword=你的密钥库密码
keyAlias=你的别名
keyPassword=你的密钥密码
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

> ⚠️ 若从其他签名的版本升级，需先卸载旧版本（签名不同无法覆盖安装）。

---

## 项目结构

```
app/src/main/java/com/diting/recorder/
├── MainActivity.kt              主界面：状态展示与录制入口
├── RecorderApp.kt                Application 入口（主题、崩溃捕获初始化）
├── RecorderLauncher.kt           增强录屏启动器
├── RecorderConfig.kt             全局配置常量
├── RecorderSettings.kt           设置存储（SharedPreferences 封装）
├── RecorderOptions.kt            录制参数数据类
├── RecorderLog.kt                日志工具
├── RootShell.kt                  Root 权限检测与执行
│
├── SettingsActivity.kt           设置主入口（三层结构）
├── RecordingSettingsActivity.kt  设置 → 录制项
├── PermissionSettingsActivity.kt 设置 → 权限项
├── AboutActivity.kt              设置 → 关于
├── ContactActivity.kt            关于 → 联系我们
├── PrivacyViewerActivity.kt      关于 → 隐私政策查看
├── LicenseActivity.kt            关于 → 开源许可（WebView 加载 LICENSE.html）
├── PrivacyActivity.kt            首次启动的隐私政策同意门槛
│
├── AudioSelfCheck.kt             音频自检逻辑
├── AudioTestActivity.kt          音频测试页
│
├── basic/                        基础录屏（MediaProjection）
│   ├── BasicRecordService.kt     前台服务：持有 MediaProjection 与通知
│   └── BasicRecordingEngine.kt   基础录制引擎
│
├── core/                         录制核心（增强模式）
│   ├── RecordingOrchestrator.kt  shell/app_process 主入口与编排
│   ├── RecordingEngine.kt        录制引擎核心逻辑
│   ├── CandidateSelector.kt      编码参数优选
│   ├── BitrateController.kt      码率控制
│   ├── FramePumper.kt            帧投递
│   ├── OutputManager.kt          输出管理
│   └── PtsNormalizer.kt          时间戳归一化
│
├── capture/
│   └── DisplayCaptureController.kt  显示采集（SurfaceControl / VirtualDisplay）
│
├── audio/
│   └── AudioCaptureFactory.kt    音频采集工厂
│
├── gl/
│   └── GlFrameRenderer.kt        OpenGL 渲染与帧同步
│
├── media/
│   ├── VideoEncoderFactory.kt    视频编码器创建
│   ├── SegmentedMp4Muxer.kt      分段 MP4 封装
│   ├── AsyncMp4MuxerKt.kt        异步封装
│   └── SharedBufferPool.kt       共享缓冲池
│
├── system/                       系统能力与隐藏 API
│   ├── ShellEnvironment.kt       shell 环境
│   ├── RecorderResourceManager.kt 资源管理
│   ├── WakeLockController.kt     唤醒锁控制
│   └── NativeCore.kt             JNI 桥接（安全转发）
│
├── ui/
│   ├── FloatingMenuController.kt 悬浮窗控制菜单
│   └── RawTouchDispatcher.kt     触摸事件分发（阻止穿透）
│
├── crash/                        崩溃捕获
│   ├── CrashHandler.kt           全局未捕获异常处理
│   └── CrashActivity.kt          崩溃日志展示与反馈
│
└── util/
    └── NotificationPermission.kt 通知权限工具
```

> 📝 注：`com/zero/recorder/system/NativeCore.kt` 为保持与预编译 native 库的 JNI 符号兼容而保留的旧包名桥接类，详见 [基于上游项目的说明](#基于上游项目的说明)。

---

## 输出文件

默认输出目录：

```text
/sdcard/Movies/Diting/Rec_YYYYMMDD_HHMMSS.mp4
```

可在「设置 → 权限项 → 保存位置」中修改输出目录（默认目录对相册可见）。

---

## 常见问题

<details>
<summary><b>录屏时没有声音？</b></summary>

系统内部声音的捕获能力取决于设备与厂商 ROM 的音频策略：

- **基础录屏**：依赖 ROM 是否允许 `AudioPlaybackCapture`，部分 ROM（如 MIUI）限制较严
- **增强录屏**：通过 Shizuku 以 shell 身份录制，成功率相对更高

可在「设置 → 权限项 → 特殊配置 → 音频自检」中先行检测设备音频能力。
</details>

<details>
<summary><b>视频只有一帧 / 画面卡住？</b></summary>

通常是 ROM 停止投递画面，或封装器等待轨道导致写入异常。可尝试：
- 更换画质（部分分辨率/码率不被硬编码器支持）
- 使用增强录屏模式
- 重启应用后重试
</details>

<details>
<summary><b>H.265 编码失败？</b></summary>

应用会自动回退到 H.264。若仍失败，多半是该分辨率/码率不被设备的硬件编码器接受。
</details>

<details>
<summary><b>通知栏没有「停止」按钮？</b></summary>

请确保已授予通知权限：
- Android 13+ 需要在「设置 → 权限项 → 通知权限」中授权
- 也可直接在主界面的橙色提醒处点击进入
</details>

<details>
<summary><b>Shizuku 授权失败？</b></summary>

请确认已安装并激活 [Shizuku](https://shizuku.rikka.app/) 应用，且已通过无线调试或 Root 启动 Shizuku 服务。
</details>

<details>
<summary><b>无法覆盖安装新版本？</b></summary>

若新旧版本签名不同，需先卸载旧版本再安装。
</details>

---

## 隐私说明

谛听**不会**：

- ❌ 申请网络权限，连接任何服务器
- ❌ 上传你的录制文件
- ❌ 收集设备信息、使用数据或任何个人信息
- ❌ 内嵌广告或第三方统计 SDK

所有录制内容仅保存在你的设备本地，完全由你掌控。

完整说明见应用内「关于 → 隐私政策」，或参阅仓库中的隐私政策文档。

---

## 基于上游项目的说明

本项目的部分底层录屏能力（私有显示接口采集、OpenGL 渲染管线、硬编码器封装、Shizuku/shell 运行框架等）源自开源项目 **ZeroRecorder**，在此基础上进行了较大规模的二次开发与品牌重构。

上游项目原始的文档已归档于 [`README_UPSTREAM.md`](README_UPSTREAM.md)，其 `LICENSE` 文件与版权声明予以完整保留。

### 主要修改内容

| 类别 | 具体修改 |
|---|---|
| **品牌重构** | 应用名改为「谛听」；包名由 `com.zero.recorder` 改为 `com.diting.recorder`；重新设计应用图标 |
| **界面重做** | 全部界面重写为原生 Material 3 风格；新增设置三层结构（录制项/权限项/关于）、音频自检页、联系与隐私政策页；图标统一改用 Tabler Icons |
| **新增基础录屏** | 新增基于 `MediaProjection` 的免权限录屏模式，并适配 Android 14+ 的「整个屏幕」授权流程 |
| **JNI 兼容修复** | 修复因包名变更导致的 native 库 JNI 符号不匹配问题（通过旧包名桥接类保持符号兼容） |
| **新增功能** | 全局崩溃捕获与反馈、通知权限引导、隐私政策首次同意门槛等 |
| **构建优化** | 启用 R8 代码压缩与资源压缩，仅保留中/英语言资源；配置正式发布签名 |

> 依据 Apache License 2.0 第 4(b) 条，上述修改声明同时发布于应用内的「开源许可」页。

---

## 开源许可

本项目以 **Apache License 2.0** 协议开源。

```
Copyright 2026 谛听（Diting）

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

完整协议文本见 [`LICENSE`](LICENSE)；可视化说明见 [`LICENSE.html`](LICENSE.html)。

### 第三方组件

| 组件 | 用途 | 许可 |
|---|---|---|
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx) | 基础 UI 兼容 | Apache-2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Material 设计组件 | Apache-2.0 |
| [Kotlin Standard Library](https://kotlinlang.org/) | 语言运行时 | Apache-2.0 |
| [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 协程并发 | Apache-2.0 |
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | 增强录屏权限框架 | Apache-2.0 |
| [Tabler Icons](https://tabler.io/icons) | 界面图标 | MIT |

---

## 联系我们

- **邮箱**：shiyun_78@proton.me
- **酷安**：嘿鹰隼

欢迎提交 Issue 或 Pull Request。如遇崩溃，应用会自动保存日志，可通过崩溃页面的「发送邮件」直接反馈，这将极大帮助我们定位问题。

---

<div align="center">

**谛听 · 听见你的屏幕**

</div>
