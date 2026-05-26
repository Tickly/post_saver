# 作品下载助手

Android 原生 App，基于个人 Android 项目模板创建，已集成 GitHub Actions 云打包。

## 项目标识

- **应用名**：作品下载助手
- **applicationId**：`com.taoguo.post_saver`
- **Gradle 项目名**：`post-saver`
- **入口**：单 Activity 占位界面，居中显示欢迎文案

## 包名命名规范

个人 App 统一采用 **`com.taoguo.<app_name>`** 格式：

| 段 | 含义 | 说明 |
|----|------|------|
| `com` | 顶级域 | 固定 |
| `taoguo` | 个人标识 | **作者个人名称**，所有从此模板衍生的 App 均保留该段，无需修改 |
| `<app_name>` | 项目名 | 小写 + 下划线，与项目/仓库名对应（如 `post_saver`、`notes`） |

本项目第三段为 `post_saver`（与仓库名对应）。`com.taoguo` 前缀保持不变。

## 复制文件夹启动新项目

1. 复制整个模板目录到新路径（例如 `D:\dev\projects\post_saver`）
2. 在新目录用 Android Studio **Open** 打开（或在 IDE 中 Rename Project）
3. **删除或重建 `.git`**（若需独立仓库：在新目录执行 `git init` 并关联新 remote，避免沿用模板的 commit 历史与 Secrets 关联）
4. 按下方「必改清单」修改标识符后，再首次 push 或运行 CI

> 每个 App 应使用**独立的 keystore** 与**独立的 GitHub Secrets**，切勿多个项目共用同一签名密钥。
>
> **为何要这样做**
>
> - Android 用签名密钥证明「这个 APK 的后续更新来自同一开发者」。Google Play 等商店会把 **applicationId + 签名** 绑定在一起；密钥一旦用于某个 App，就应视为该 App 的「身份证」。
> - 各项目使用独立 GitHub 仓库时，Secrets 也应一一对应，避免 A 项目的 CI 误用 B 项目的密钥，或某个仓库泄露后牵连其他 App。
>
> **共用同一密钥的风险**
>
> - **更新链绑定**：多个 App 共用密钥后，若其中一个项目泄露或需要轮换密钥，其他 App 的已发布版本与后续更新都会受影响，Play 控制台也可能要求统一处理。
> - **无法独立撤销权限**：某个仓库的协作者、Actions 日志或 fork 若暴露 base64 keystore，所有共用该密钥的 App 均面临被伪造更新的风险，无法只「吊销」某一个 App 的签名。
> - **上架与转移困难**：转让、下架或分拆某个 App 时，签名与多个包名绑在一起，后续维护和责任边界不清晰。
> - **调试与正式包混淆**：若 Debug/Release 或不同项目共用同一 alias/密码习惯，容易配错 Secret，导致 CI 打出错误签名或构建失败。
>
> **建议做法**：每个 App 单独生成 keystore，仅在该 App 的 GitHub 仓库配置 4 个 Secrets，并妥善离线备份 keystore 与密码（丢失后无法为同一 applicationId 发布兼容更新）。具体步骤见下文「创建 App 专用 keystore」。

## 必改清单

按推荐顺序逐项修改。优先级 P0 为启动前必改，P1 为上架或推仓库前建议完成。

| 优先级 | 文件 | 修改项 | 模板默认值 |
|--------|------|--------|------------|
| P0 | [app/build.gradle.kts](app/build.gradle.kts) | `namespace`、`applicationId` 第三段 | `post_saver`（已完成） |
| P0 | [app/build.gradle.kts](app/build.gradle.kts) | `versionCode`、`versionName` | `1` / `0.1.0` |
| P0 | [app/src/main/java/com/taoguo/post_saver/MainActivity.kt](app/src/main/java/com/taoguo/post_saver/MainActivity.kt) | `package` 声明第三段 | 与 `namespace` 一致 |
| P0 | 同上目录 | **重命名** Kotlin 源码目录 | `com/taoguo/post_saver/` |
| P0 | [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) | `app_name` | `作品下载助手` |
| P1 | [settings.gradle.kts](settings.gradle.kts) | `rootProject.name` | `post-saver` |
| P1 | Launcher 资源 | 替换图标 | 见「图标与品牌」 |
| P1 | 新 GitHub 仓库 | 配置 4 个签名 Secrets | 见「GitHub Actions 云端构建」 |

> `com.taoguo` 前缀在所有项目中固定不变，复制后主要修改第三段 `<app_name>` 及对应源码目录。

### 包名示例（本项目）

| 位置 | 值 |
|------|-----|
| `app/build.gradle.kts` → `namespace` | `com.taoguo.post_saver` |
| `app/build.gradle.kts` → `applicationId` | `com.taoguo.post_saver` |
| Kotlin 源码目录 | `app/src/main/java/com/taoguo/post_saver/` |
| `MainActivity.kt` 首行 | `package com.taoguo.post_saver` |

`AndroidManifest.xml` 中 Activity 使用相对类名 `.MainActivity`，会随 `namespace` 自动解析，**无需**在 Manifest 中写全限定类名。

### Debug 后缀说明

模板为 Debug 构建添加了后缀，便于与 Release 版共存：

```kotlin
getByName("debug") {
    applicationIdSuffix = ".debug"
    versionNameSuffix = "-debug"
}
```

例如 Release 包名为 `com.taoguo.post_saver`，Debug 包名为 `com.taoguo.post_saver.debug`。若不需要与正式版共存，可删除这两行。

## 图标与品牌

上架前建议替换启动器图标。当前模板仅有 adaptive 矢量资源，未包含 `mipmap-hdpi` 等传统位图目录。

需关注或替换的文件：

- [app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
- [app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml](app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml)
- [app/src/main/res/drawable/ic_launcher_foreground.xml](app/src/main/res/drawable/ic_launcher_foreground.xml)
- [app/src/main/res/values/ic_launcher_background.xml](app/src/main/res/values/ic_launcher_background.xml)

建议使用 Android Studio **Image Asset**（`File → New → Image Asset`）一键生成全套 `mipmap-*dpi` 资源。

## 可选调整

- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) — `welcome_message` 等界面文案
- [app/src/main/res/values/themes.xml](app/src/main/res/values/themes.xml) — 主题色与 Material 配色
- [app/build.gradle.kts](app/build.gradle.kts) — `minSdk` / `targetSdk` / `compileSdk`（修改 `compileSdk` 时需同步 [.github/workflows/android-release-apk.yml](.github/workflows/android-release-apk.yml) 中 `sdkmanager` 安装的 platform 版本）
- 本文档顶部「项目标识」— 同步更新为新应用名与 applicationId

## 本地开发

```bash
./gradlew :app:assembleDebug
```

> 本地完整离线构建需要 `gradle/wrapper/gradle-wrapper.jar`；CI 通过 `setup-gradle` 指定版本，不依赖该文件。

## GitHub Actions 云端构建

Workflow：[.github/workflows/android-release-apk.yml](.github/workflows/android-release-apk.yml)（push 到 `main` 或手动触发）。

签名逻辑已在 [app/build.gradle.kts](app/build.gradle.kts) 中通过环境变量注入，**无需修改 Gradle 代码**。每个新项目在新 GitHub 仓库中单独配置 Secrets 即可。

若新仓库的默认分支不是 `main`，需修改 workflow 中 `on.push.branches` 配置。

### 创建 App 专用 keystore

每个 App **单独生成一份** keystore 文件，不要复用其他项目的 keystore。以下以本项目 `post_saver` 为例，文件名与 alias 可按项目自行命名。

**1. 使用 keytool 生成（JDK 自带）**

在终端执行（会交互式提示设置密码与证书信息；姓名/组织等可填个人或项目名，也可直接回车跳过）：

```bash
keytool -genkeypair -v \
  -keystore post-saver-release.keystore \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias post_saver
```

上述命令**无需** `-storetype`，JDK 会使用默认格式，Android 签名与本模板 CI 均兼容。

执行完成后请记录这 4 项（后续填入 GitHub Secrets）：

| 项 | 对应 Secret | 说明 |
|----|-------------|------|
| keystore 文件 | `ANDROID_KEYSTORE_BASE64` | 整个文件的 base64 |
| keystore 密码 | `KEYSTORE_PASSWORD` | 上一步 `-keystore` 时设置的 store 密码 |
| `-alias` 的值 | `KEY_ALIAS` | 上例为 `post_saver` |
| key 密码 | `KEY_PASSWORD` | 若与 store 密码相同，填同一值即可 |

**2. 将 keystore 转为 base64**

Windows（PowerShell）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("post-saver-release.keystore"))
```

Linux：

```bash
base64 -w 0 post-saver-release.keystore
```

macOS：

```bash
base64 -i post-saver-release.keystore
```

将输出的**整段字符串**复制为 Secret `ANDROID_KEYSTORE_BASE64` 的值（不要换行、不要加引号）。

**3. 备份与安全**

- 将 keystore 文件与密码保存到**离线安全位置**（密码管理器、加密 U 盘等）；丢失后无法为已上架的同一 `applicationId` 发布兼容更新。
- **切勿**将 keystore 或 base64 内容提交到 Git 仓库；仅通过 GitHub Secrets 注入 CI。
- 本地调试 Release 签名时，可临时设置环境变量 `KEYSTORE_FILE`（指向 keystore 绝对路径）及另外 3 个密码/alias 变量，与 CI 使用同一套值即可。

### 需要配置的 GitHub Secrets

在仓库 `Settings → Secrets and variables → Actions → Repository secrets` 中添加：

| Secret | 说明 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 base64 编码 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 密码 |

### 获取 APK

运行 workflow 后，在 Actions 运行记录页面下载 artifact：`app-release-apk`。

## 验证构建

完成必改清单并配置 Secrets 后：

1. Push 到默认分支（`main`），或在 GitHub Actions 页面手动触发 `android-release-apk`
2. 确认 workflow 运行成功
3. 下载 artifact `app-release-apk` 并安装验证

## 快速自检清单

模板迁移完成后，可按此清单逐项勾选：

- [x] `namespace` / `applicationId` 第三段已改为 `post_saver`
- [x] Kotlin 源码目录与 `package` 声明已对齐（`com/taoguo/post_saver/`）
- [x] `app_name` 已修改为「作品下载助手」
- [x] `rootProject.name` 已修改为 `post-saver`
- [ ] 启动器图标已替换
- [ ] 新 GitHub 仓库已配置独立 keystore 与 4 个 Secrets
- [x] README 中的项目标识已更新
- [ ] CI 构建成功，APK 可正常安装
