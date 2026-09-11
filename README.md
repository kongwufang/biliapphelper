# bapp helper

**哔哩哔哩 App 的轻量化辅助工具。**

把官方 App 里藏得比较深、或者要来回跳好几个页面才能完成的几个账号管理操作，收拢到一个轻量客户端里完成。

它不是接口调试工具，也不做任何内容抓取或下载——它做的事情和你在官方 App「我的 → 设置」里点来点去完全一样，只是路径更短。

## 功能

| 功能 | 说明 |
| --- | --- |
| 密码登录 | 账号密码登录；遇到设备验证 / 风险验证时自动走极验 + 短信验证码 |
| 刷新凭证 | 查看 access_token 剩余有效期，并一键续期 |
| 扫码登录 | 用本机登录态授权网页端 / TV 端扫码登录 |
| 空间隐私设置 | 查看并批量修改个人空间的各项展示开关 |
| 账号安全 | 登录保护（二次验证）开关、一键退登所有网页与 PC 客户端 |
| 设备管理 | 查看所有登录设备，并移除陌生设备 |
| 运行日志 | 集中查看每一步操作的过程日志 |

## 界面说明

- **首页**：登录状态卡片 + 功能入口。
- **功能页**：进入后自动加载当前状态，不需要先手动点「查询」。
- **结果提示**：操作成功 / 失败以 Toast 即时提示；详细过程日志收在「运行日志」二级页面，不占用主界面。

## 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | compileSdk 35（build-tools 35 及以上） |
| Gradle | 9.x |
| minSdk | 24（Android 7.0） |

## 构建

1. 在项目根目录创建 `local.properties`，指向本机 Android SDK：

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

2. 执行构建：

   ```bash
   gradle assembleRelease
   ```

   产物位于 `app/build/outputs/apk/release/app-release.apk`。

   也可以直接用 Android Studio 打开项目构建（IDE 会自动补上 Gradle Wrapper）。

> `build_apk.sh` 是作者在 WSL 环境里使用的构建脚本，内含固定的本机路径，仅作参考。

> 当前 release 使用 debug 签名（见 `app/build.gradle.kts` 中的 `signingConfig = signingConfigs.getByName("debug")`），方便直接安装体验；如需正式发布请替换为自己的 keystore。

## 关于设备指纹

哔哩哔哩会对登录设备做指纹校验。本项目的设备指纹已**完整内置在工程里**，克隆后可直接构建运行，无需任何额外准备：

- **持久标识**（`buvid` / `device_id` / `local_id` / `login_session_id`）硬编码在 `DeviceFingerprint.kt`；
- **硬件指纹**（`device_meta` / `dt`）放在 `app/src/main/assets/device_fp.json`，已随仓库一起分发。

> 以上指纹取自作者本人的设备，仅供个人自用参考。若多人使用或换到其它设备，建议替换为你自己的值，否则各个安装会共用同一条登录设备记录。

## 说明

- 所有请求直接发往哔哩哔哩官方接口，签名算法与官方 App 保持一致，没有第三方服务器参与。
- 本项目面向个人账号的日常管理，请勿用于批量操作或其他违反哔哩哔哩用户协议的用途。
