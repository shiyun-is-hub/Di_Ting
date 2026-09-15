# ZeroRecorder

一个 Android 录屏工具，支持两种运行方式：

1. **Activity 界面模式**：通过主界面操作，需 Shizuku 授权
2. **Shell 后台模式**：通过 `adb shell` 或 `adb root` + `app_process` 启动，需 shell/root 权限

ZeroRecorder is an Android screen recorder that supports two operation modes:

1. **Activity mode**: Operate via main UI, requires Shizuku authorization
2. **Shell background mode**: Launched via `adb shell` or `adb root` + `app_process`, requires shell/root privileges

主要能力：

- 使用私有显示接口采集屏幕（优先 `SurfaceControl`，必要时回退到 `VirtualDisplay`）
- 使用 OpenGL 渲染路径做旋转/缩放并喂给硬编码器
- 尝试采集系统播放声音（ROM/权限允许时），并封装为 MP4
- 输出到设备：`/sdcard/Movies/ZeroRecorder/`
- 悬浮窗控制菜单，支持录制状态显示和触摸操作
- 通过底层输入设备读取实现触摸事件阻止穿透

Key features:

- Capture screen frames via private display APIs (prefer `SurfaceControl`, fallback to `VirtualDisplay` when needed)
- Use an OpenGL render path for rotation/scale and feed frames into the hardware encoder
- Best-effort internal audio capture (when the ROM/privileges allow), muxed into MP4
- Output directory on device: `/sdcard/Movies/ZeroRecorder/`
- Floating control menu with recording status display and touch interaction
- Touch event blocking via direct input device access

## 依赖 / Dependencies

- **Shizuku**：用于 Activity 模式下获取权限（`dev.rikka.shizuku:api:13.1.5`）
- **Android SDK**：包含 `adb`
- **Android Studio JBR** 或兼容 JDK

## 适用范围与风险 / Scope & Risks

本项目使用隐藏/私有 Android API（反射 + 系统上下文绕过）。兼容性强依赖：

- Android 版本（尤其 Android 13+ 的音频策略和触摸事件限制）
- 厂商 ROM 行为（MIUI 等更严格，可能导致无声或帧投递不稳定）
- Shizuku/shell 权限与系统服务限制
- 触摸事件阻止穿透在 Android 13+ 上依赖设备支持

建议把它当作工程工具使用，而不是面向普通用户的"通用录屏 App"。

This project uses hidden/private Android APIs (reflection + system context workarounds). Compatibility heavily depends on:

- Android version (especially audio policy and touch event restrictions on Android 13+)
- Vendor ROM behavior (MIUI and others may be stricter, causing no-audio or unstable frame delivery)
- Shizuku/shell privileges and system service restrictions
- Touch blocking effectiveness varies by Android version

Treat it as an engineering tool rather than a production-ready consumer recorder.

## 目录结构 / Project Layout

- `app/src/main/java/com/zero/recorder/MainActivity.kt`：主界面 Activity，通过 Shizuku 授权启动录制
- `app/src/main/java/com/zero/recorder/core/RecordingOrchestrator.kt`：Shell 模式主入口与录制编排
- `app/src/main/java/com/zero/recorder/core/RecordingEngine.kt`：录制引擎核心逻辑
- `app/src/main/java/com/zero/recorder/audio/`：音频采集（AudioCaptureFactory）
- `app/src/main/java/com/zero/recorder/capture/`：显示采集（DisplayCaptureController）
- `app/src/main/java/com/zero/recorder/gl/`：OpenGL 渲染与帧同步（GlFrameRenderer）
- `app/src/main/java/com/zero/recorder/media/`：视频编码与 MP4 封装（VideoEncoderFactory、SegmentedMp4Muxer）
- `app/src/main/java/com/zero/recorder/system/`：Shell/System Context 与隐藏 API 绕过
- `app/src/main/java/com/zero/recorder/ui/`：悬浮窗控制菜单与触摸事件分发

## 环境要求 / Requirements

- Windows（自带 `manage.ps1`）
- Android SDK（包含 `adb`）
- Android Studio JBR 或兼容 JDK
- 一台允许 Shizuku/shell 访问相关显示/音频路径的设备

脚本默认路径可能与你的机器不同，需要按实际环境调整：

- Android SDK（示例）：`C:\Users\YourUsername\AppData\Local\Android\Sdk`
- JBR（示例）：`C:\Program Files\Android\Android Studio\jbr`

You may need to adjust `manage.ps1` to match your local SDK/JDK paths.

## 构建 / Build

```powershell
.\manage.ps1 -Build
```

或直接：

```powershell
.\gradlew.bat assembleDebug
```

## 运行 / Run

### Activity 模式（推荐）

通过 Android 设备上的应用图标启动，界面包含：

- **状态卡片**：显示 Shizuku 运行状态和授权状态
- **开始录制按钮**：请求 Shizuku 授权并启动后台录制
- **停止录制按钮**：强行停止功能，用于清理异常残留的录制进程

### Shell 模式

```powershell
.\manage.ps1 -Run
```

或构建并运行：

```powershell
.\manage.ps1 -All
```

安装 APK 到设备：

```powershell
.\manage.ps1 -Install
```

清理构建缓存：

```powershell
.\manage.ps1 -Clean
```

或手动执行：

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Users\ly775\AppData\Local\Android\Sdk"
.\gradlew.bat assembleDebug -q --console plain
$ADB="$env:ANDROID_HOME\platform-tools\adb.exe"
& $ADB push "app\build\outputs\apk\debug\app-debug.apk" /data/local/tmp/zero_recorder.apk
& $ADB shell "export CLASSPATH=/data/local/tmp/zero_recorder.apk; app_process / com.zero.recorder.core.RecordingOrchestrator"
```

执行流程：

1. 推送 debug APK 到设备
2. 在设备端通过 `app_process` 执行 Java `main()`
3. 显示悬浮窗控制菜单
4. 保存运行日志到 `.\logs`

What the runner does:

1. Push the debug APK to the device
2. Execute the Java `main()` entrypoint via `app_process`
3. Display floating control menu
4. Save logs to `.\logs`

## 输出文件 / Output

录制文件写入设备：

```text
/sdcard/Movies/ZeroRecorder/Rec_YYYYMMDD_HHMMSS.mp4
```

## 悬浮窗控制 / Floating Menu

悬浮窗提供以下功能：

- **非录制状态**（三格布局）：左侧红色圆形录制按钮、中间三个白点状态指示、右侧 X 关闭按钮
- **录制状态**（两格布局）：左侧绿色方形停止按钮、中间录制计时器
- **拖拽移动**：触摸悬浮窗并移动可拖动位置

悬浮窗大小使用 dp 动态计算，在不同分辨率设备上保持一致的物理尺寸。

Floating menu features:

- **Idle state** (3 cells): Left red circle record button, center three white dots indicator, right X close button
- **Recording state** (2 cells): Left green square stop button, center recording timer
- **Drag to move**: Touch and move the menu to reposition

The floating menu size is calculated dynamically using dp units, maintaining consistent physical size across different resolution devices.

## 日志与排障 / Logs & Troubleshooting

`manage.ps1` 会生成：

- `logs/console_*.txt`：标准输出/错误输出（建议优先看）
- `logs/logcat_zero_*.txt`：过滤后的 recorder 日志

关键日志 tag（manage.ps1 过滤的是 `ZR.Core`、`ZR.Display`、`ZR.GL`）：

- `ZR.Core`
- `ZR.Display`
- `ZR.GL`
- `ZR.Engine`
- `ZR.Orchestrator`

常见问题定位：

- H.265 失败：会自动回退 H.264；若仍失败，多半是分辨率/码率不被硬编码器支持
- 视频只有一帧/卡住：通常是 ROM 停止投递画面或封装器等待轨道导致写入异常
- 无声：多半是 ROM 不允许 shell/Shizuku attribution 的 playback capture，或者音频源被策略拒绝
- 触摸事件穿透：在 Android 13+ 上正常工作
- Shizuku 授权失败：确保已安装并激活 Shizuku Manager

Artifacts generated by `manage.ps1`:

- `logs/console_*.txt`: stdout/stderr (best first stop)
- `logs/logcat_zero_*.txt`: filtered recorder logs

Common issues:

- H.265 fails: falls back to H.264; if it still fails, the device encoder likely rejects the chosen resolution/bitrate
- Only one frame / frozen video: usually ROM stops delivering frames, or muxing/track readiness issues
- No audio: ROM rejects playback capture for shell/Shizuku attribution, or audio policy denies the source
- Touch event passthrough: works normally on Android 13+;
- Shizuku authorization failed: ensure Shizuku Manager is installed and activated

## License / 许可证

Apache License 2.0，见 [`LICENSE`](LICENSE)。
