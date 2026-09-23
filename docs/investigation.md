# 调查：2026-09-23

## 输入包和手机

`E:\software\bili-airborne\installed-backup.apk`：

| 项目 | 实际结果 |
| --- | --- |
| 包名 | tv.danmaku.bili |
| versionName / code | 8.99.0 / 8990400 |
| min / target SDK | 24 / 35 |
| ABI | arm64-v8a |
| 证书 DN | CN=BiliRoamingX |
| 证书 SHA-256 | 4ac19c0edb79427fa4f31b71dc32f362336e6f5e56b1fdfcde639b30176a7f08 |
| APK SHA-256 | a8784d0622f49357857d4a9fd75df1a90c19b0aee31cc30e87a846cdf9672fc5 |

由 aapt dump badging、apksigner verify --print-certs、Get-FileHash 实测。该包已经过修改，不能作为官方原版基线。手机只读取了包版本与设备信息，没有安装、卸载、导出账号或修改设置。手机报告同样的 8.99.0 / 8990400；未另行验证手机现有包文件哈希。

## 框架

- [BiliRoamingX](https://github.com/BiliRoamingX/BiliRoamingX)：已有原版界面、资源入口、广告及播放器指纹；静态补丁适合无 Root 设备。选作初始源码基础，但并不代表已解决 8.99.0 适配。
- 官方公开 main 与调查过的 sti-233/test 都解析为 `ae58109f3acdd53ec2d2b3fb439c2a2ef1886221`（2024-09-24），源码版本 1.23.3，不包含提供包里的现代空降实现。正式维护需扩展并验证指纹，不能以仓库最近推送时间推断源码功能更新。
- [LSPosed/LSPatch](https://github.com/LSPosed/LSPatch) 原仓库归档；运行时框架额外引入 Android 版本兼容性。此次不选它作为第一实现。
- PiliPlus 不作为宿主；仅参考公开空降接口的 BV/CID 参数约定。

本项目使用源码覆盖层固定基线，以避免直接改写旧目录和二次修改现有成品包。首版正式发布应减少补丁选择，禁止默认启用区域、会员或清晰度等无关功能。

## 官方基线和 AVD

[官方客户端下载页面](https://app.bilibili.com/)指向 `https://dl.hdslb.com/mobile/latest/android64/iBiliPlayer-bili.apk`。已开始下载；下载完成前不能宣称版本或签名验证成功。

发现 SDK `E:\software\androidsdk` 内有 emulator 36.3.10 和 Android 36.1 Google Play x86_64 系统镜像。`emulator -accel-check` 返回 WHPX 可用。创建 AVD `bili-clean-api36`，路径 `E:\software\bili-clean-avd`。ARM64 翻译播放能力尚未验证。

后台启动模拟器与下载源码的组合命令被自动审批拒绝，工具仅返回 `blocked by policy`，没有进一步原因。后续改为工具直接托管的单独无窗口模拟器命令，该方式获准执行。修正进程级 SDK 路径后进入冷启动；安装和播放尚未执行。

## 签名

修改 APK 会破坏原签名，必须使用自有签名重新签署。相同包名但签名不同，常规 Android 安装不能覆盖；已有 BiliRoamingX 证书私钥也不在本项目掌握中。证书指纹不是私钥，不能靠复制指纹实现同签名更新。

来源：[Android 应用签名文档](https://developer.android.com/studio/publish/app-signing)。手机安装方案见独立文档，在 AVD 验证通过后才进入选择阶段。
