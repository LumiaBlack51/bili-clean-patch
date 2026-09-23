# bili-clean-patch（AVD 实验预览版）

独立于 PiliPlus 的 Android 原版哔哩哔哩静态补丁项目。保留宿主界面，第一阶段实现界面广告过滤和社区广告片段自动跳过。**实验适配官方 9.12.0 (9120300)：AVD 启动、播放、设置和真实视频空降已通过；去广告通过真实宿主模型的受控测试，但线上真实广告样本、开屏和视频页覆盖尚未全面验收。不是稳定版。**

[完整 APK 与加密签名备份下载](https://github.com/LumiaBlack51/bili-clean-patch/releases/tag/v0.1.0-avd-preview)（私有仓库登录后可见）。发布资产的远端 SHA-256 已逐一与本地核对。

## 当前实现

- 固定 BiliRoamingX GPL-3.0 源码提交，使用 ReVanced 静态补丁和现有设置入口。
- 设置页顶部展示去广告、空降助手、自动跳过和提示开关；不查询账号等级或大会员状态，去除上游设置层账号黑名单判断。
- 独立推荐流和 JSON 广告过滤，按 9.12.0 实际模型适配开屏；复用部分视频页面过滤。尚未证明覆盖所有界面广告。
- 独立空降策略引擎、只读社区接口客户端、BV/CID/时长校验、广告区间提示、播放器 seek 接入。仅处理 `sponsor` + `skip`；一次成功发起跳转后允许手动回看。
- 网络请求不携带账号 cookie，不写入社区数据库。HTTP 404/空列表、请求错误、播放器异常分别处理。

## 构建

PowerShell 7.4+、JDK 17、Git、Android SDK（API 35、Build Tools 35.0.0、NDK 28.2.13676358、CMake 3.22.1）。本项目将上游的 Java 11 补丁编译目标更新到 17，运行 CLI 也需要 JDK 17。依赖源包括 Google、Maven Central、JitPack。

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

脚本核验 CLI 和宿主 SHA-256，显式选择首批补丁，检测失败日志，使用 `local/avd-test.keystore` 生成测试签名。不得用手机备份的已改包 APK 替代原版输入。本次交付使用同一测试签名保持更新连续性；新建密钥会导致同包名无法覆盖更新。

## 验证和交付

- [调查和方案](docs/investigation.md)
- [交付 APK 的实际测试与支持边界](docs/candidate-9.md)
- [历次测试记录](docs/test-record.md)
- [推荐流适配与误删修正](docs/feed-investigation.md)
- [真实宿主模型的受控测试](docs/host-model-tests.md)
- [重签名运行兼容及对照证据](docs/native-compat.md)
- [手机安装与回滚方案](docs/install-rollback.md)
- `evidence/` 保存公开片段接口的实际返回。

`local/`、`upstream/`、APK、密钥文件均忽略。`scripts/backup-signing-key.ps1` 生成 AES-256-GCM 加密的 BKS 签名备份，并验证解密一致性。可单独上传 `local/signing-key.encrypted.json`；恢复密钥 `local/signing-recovery-key.bin` 保留本地并另外保管，不与加密文件一起上传。恢复方法：

```powershell
./scripts/restore-signing-key.ps1 -BackupPath ./signing-key.encrypted.json -RecoveryKeyPath ./signing-recovery-key.bin -OutputKeystore ./local/avd-test.keystore
```

恢复脚本拒绝覆盖已有密钥。重新构建前应恢复同一签名密钥，否则 CLI 会生成另一把密钥，Android 无法将其作为原测试包的更新。当前证书是测试用途，不是官方签名。没有导出手机的账号数据库、cookie 或令牌；APK 本身不包含这些账号数据。

本项目代码按 GPL-3.0 提供；上游代码许可见 LICENSE。PiliPlus 仅用于研究空降接口约定，没有修改或转换其客户端代码。
