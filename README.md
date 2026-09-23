# bili-clean-patch（开发中，未通过客户端验收）

独立于 PiliPlus 的 Android 原版哔哩哔哩静态补丁项目。保留宿主界面，第一阶段实现界面广告过滤和社区广告片段自动跳过。**当前不是可用发布版，已产出实验 APK，但尚无通过运行验收的支持版本。**

## 当前实现

- 固定 BiliRoamingX GPL-3.0 源码提交，使用 ReVanced 静态补丁和现有设置入口。
- 设置页顶部展示去广告、空降助手、自动跳过和提示开关；不查询账号等级或大会员状态，去除上游设置层账号黑名单判断。
- 独立推荐流和 JSON 广告过滤，按 9.12.0 实际模型适配开屏；复用部分视频页面过滤。尚未证明覆盖所有界面广告。
- 独立空降策略引擎、只读社区接口客户端、BV/CID/时长校验、广告区间提示、播放器 seek 接入。仅处理 `sponsor` + `skip`；一次成功发起跳转后允许手动回看。
- 网络请求不携带账号 cookie，不写入社区数据库。HTTP 404/空列表、请求错误、播放器异常分别处理。

## 构建

Windows PowerShell、JDK 17、Git、Android SDK（API 35、Build Tools 35.0.0、NDK 28.2.13676358、CMake 3.22.1）。本项目将上游的 Java 11 补丁编译目标更新到 17，运行 CLI 也需要 JDK 17。依赖源包括 Google、Maven Central、JitPack。

```powershell
./scripts/fetch-original.ps1
./scripts/test.ps1
./scripts/build.ps1
```

下载脚本只接受已调查的 9.12.0 原包哈希；官方 latest 一旦改变便停止。也可自行将相同哈希的原包放到 `local/official.apk`。构建时优先使用 `ANDROID_HOME`，其次 `ANDROID_SDK_ROOT`，否则使用 Windows 默认 SDK 目录；脚本检查 SDK 组件并生成未入库的 `upstream/local.properties`。首次下载 CLI 需要 GitHub CLI（`gh`）。

`prepare.ps1` 核验上游提交并覆盖本项目源文件；重复运行不会重复插入设置和 hook。上游依赖 `kofua.app.revanced:revanced-patcher:19.3.1` 的 GitHub Packages 地址实测匿名 HTTP 401；本项目改从公开 CLI v4.6.0.2 的 fat JAR 引用引擎（该 CLI 源码声明 19.3.1.2），已通过完整构建和静态补丁试验。

构建脚本限制两个 worker，结束后退出 Gradle daemon。在 16 GB 主机上应先构建，再启动 AVD；同时运行两者曾导致内存压力和原版 ANR。Dobby 的 Android 日志头文件已移到文件作用域以兼容 NDK 28。

对固定哈希的原版 9.12.0 生成实验候选：

```text
./scripts/patch-experimental.ps1
```

脚本核验 CLI 和宿主 SHA-256，显式选择首批补丁，检测失败日志，使用 `local/avd-test.keystore` 生成测试签名。不得用手机备份的已改包 APK 替代原版输入。正式交付前必须完成运行验收并明确长期签名策略。

## 验证和交付

- [调查和方案](docs/investigation.md)
- [实际测试记录](docs/test-record.md)
- [手机安装与回滚方案](docs/install-rollback.md)
- `evidence/` 保存公开片段接口的实际返回。

`local/`、`upstream/`、APK、密钥文件均忽略。账号数据和签名私钥不放入源码 Git 历史；如需异地备份，应另行设计加密备份和密钥保管方式。

本项目代码按 GPL-3.0 提供；上游代码许可见 LICENSE。PiliPlus 仅用于研究空降接口约定，没有修改或转换其客户端代码。
