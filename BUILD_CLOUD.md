# 云构建指引（GitHub Actions）

本文档说明如何使用 GitHub Actions 在云端自动构建谛听的 APK。

> **为什么需要云构建？**
> 本地开发环境是 arm64 架构，Google 官方不提供 arm64 版的新版 aapt2，
> 导致无法本地编译 `targetSdk 35`。GitHub Actions 提供 x86_64 环境，
> 可以无障碍构建 targetSdk 35 的包（满足 Google Play 上架要求）。

---

## 一、前置准备

### 1.1 已具备

- ✅ GitHub 账号
- ✅ 已创建的仓库（建议设为 **Public**，Actions 免费无限）
- ✅ 本地已有签名密钥库 `diting-release.jks`

### 1.2 需要准备

- 密钥库信息（4 项）：
  - 密钥库文件的 base64 编码
  - 密钥库密码（storePassword）
  - 密钥别名（keyAlias）
  - 密钥密码（keyPassword）

---

## 二、配置 GitHub Secrets（关键）

进入仓库页面：

```
Settings → Secrets and variables → Actions → New repository secret
```

依次添加以下 4 个 Secret：

| Secret 名称 | 值 | 说明 |
|---|---|---|
| `KEYSTORE_BASE64` | 密钥库的 base64 字符串 | 见下方生成方法 |
| `KEYSTORE_PASSWORD` | 密钥库密码 | |
| `KEY_ALIAS` | `diting` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 | 通常与密钥库密码相同 |

### 生成 KEYSTORE_BASE64

**方法一：在 Linux / macOS / Termux 上**

```bash
base64 -w 0 diting-release.jks
```

**方法二：在 Windows（PowerShell）上**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("diting-release.jks"))
```

**方法三：在 Android 手机上（本机操作）**

```bash
# 在终端中执行（需 root 或使用 Operit 终端）
base64 -w 0 /sdcard/下载/签名/diting-release.jks
```

> 💡 复制输出的**一整行**字符串（可能很长），粘贴到 GitHub Secret 的值中。
>
> ⚠️ **切勿**把密钥库或 base64 字符串提交到代码仓库或公开发布。

---

## 三、触发构建

### 方式 1：推送到主分支（自动）

```bash
git push origin main
```

### 方式 2：手动触发

```
仓库 → Actions → Build APK → Run workflow → 选择分支 → Run
```

### 方式 3：推送版本标签（自动创建 Release）

```bash
git tag v1.0.0
git push origin v1.0.0
```

这会自动创建一个 GitHub Release，并附带 APK 文件。

---

## 四、获取构建产物

### 4.1 从 Actions 页面下载

```
仓库 → Actions → 点击某次构建 → 页面底部 Artifacts → 下载 Diting-APK
```

> Artifacts 保留 30 天。

### 4.2 从 Release 页面下载（打标签时）

```
仓库 → Releases → 选择版本 → 下载 APK
```

---

## 五、工作流说明

工作流文件位于 `.github/workflows/build.yml`，包含以下步骤：

| 步骤 | 说明 |
|---|---|
| Checkout | 检出仓库代码 |
| Set up JDK 17 | 配置 Java 17 环境 |
| Set up Android SDK | 安装 android-35 平台与 build-tools 35.0.0 |
| Cache Gradle | 缓存 Gradle 依赖，加速后续构建 |
| Decode keystore | 从 Secrets 解码密钥库并生成 keystore.properties |
| Grant execute permission | 赋予 gradlew 执行权限 |
| Build Release APK | 执行 `./gradlew assembleRelease` |
| Rename APK | 重命名为 `Diting-v{版本号}.apk` |
| Upload Artifact | 上传构建产物 |
| Create Release | 打标签时自动创建 Release |

---

## 六、常见问题

### Q1：构建失败，提示找不到 android-35？

检查工作流中的 `packages` 配置是否正确：

```yaml
packages: 'platforms;android-35 build-tools;35.0.0 platform-tools'
```

### Q2：签名失败 / APK 未签名？

确认 4 个 Secrets 都已正确配置，且 `KEYSTORE_BASE64` 是**单行**的完整字符串。

### Q3：Actions 显示"分钟数不足"？

- **Public 仓库**：Actions 免费无限 ✅
- **Private 仓库**：每月 2000 分钟免费额度

### Q4：构建出的 APK 和本地的不一样？

云端使用正式签名，本地（无 keystore.properties 时）使用 debug 签名。
两者的**签名不同**，无法互相覆盖安装，属正常现象。

### Q5：可以缩短构建时间吗？

已启用 Gradle 缓存。首次构建约 3~5 分钟，后续约 1~2 分钟。

---

## 七、安全提醒

| ✅ 应该做 | ❌ 不要做 |
|---|---|
| 密钥库存到 GitHub Secrets | 把 `.jks` 提交到仓库 |
| 确认 `.gitignore` 包含 `*.jks` | 把 `keystore.properties` 提交到仓库 |
| 密钥库额外离线备份 | 把密钥库密码写在代码或文档里 |
| 定期检查 Secrets 配置 | 在公开 Issue 中粘贴密钥信息 |

---

## 八、本地构建 vs 云构建

| 场景 | 推荐方式 |
|---|---|
| 日常开发调试 | 本地构建（快，可即时验证） |
| 出正式发布包 | 云构建（正式签名，支持 targetSdk 35） |
| 上架应用商店 | 云构建 |
| 快速功能验证 | 本地构建 |

> ⚠️ 注意：本地环境为 arm64，**无法构建 targetSdk 35**。
> 因此发布正式包必须走云构建。