# bili-clean-patch（开发中，未通过客户端验收）

独立于 PiliPlus 的 Android 原版哔哩哔哩静态补丁项目。保留宿主界面，第一阶段实现界面广告过滤和社区广告片段自动跳过。**当前不是可用发布版，尚无经过验证的支持版本或完整 APK。**

## 当前实现

- 固定 BiliRoamingX GPL-3.0 源码提交，使用 ReVanced 静态补丁和现有设置入口。
- 设置页顶部展示去广告、空降助手、自动跳过和提示开关；不查询账号等级或大会员状态，去除上游设置层账号黑名单判断。
- 复用上游开屏、推荐流和部分视频页面广告过滤。尚未证明覆盖所有界面广告。
- 独立空降策略引擎、只读社区接口客户端、BV/CID/时长校验、广告区间提示、播放器 seek 接入。仅处理 `sponsor` + `skip`；一次成功发起跳转后允许手动回看。
- 网络请求不携带账号 cookie，不写入社区数据库。HTTP 404/空列表、请求错误、播放器异常分别处理。

## 构建

Windows PowerShell、JDK 17、Git、Android SDK（API 35、Build Tools 35.0.0、NDK 28.2.13676358、CMake 3.22.1）。本项目将上游的 Java 11 补丁编译目标更新到 17，运行 CLI 也需要 JDK 17。依赖源包括 Google、Maven Central、JitPack。

```powershell
./scripts/test.ps1
gh release download v4.6.0.2 --repo zjns/revanced-cli --pattern revanced-cli.jar --dir local
./scripts/prepare.ps1
git -C upstream submodule update --init --recursive
cd upstream
./gradlew.bat dist --console=plain
```

`prepare.ps1` 核验上游提交并覆盖本项目源文件；重复运行不会重复插入设置和 hook。上游依赖 `kofua.app.revanced:revanced-patcher:19.3.1` 的 GitHub Packages 地址实测匿名 HTTP 401；本项目改从公开 CLI v4.6.0.2 的 fat JAR 引用引擎（该 CLI 源码声明 19.3.1.2），尚需完整构建验证二进制兼容性。

上游文档中的补丁命令如下，**本项目尚未运行到该步骤；不是已验证的发布命令**：

```text
java -jar revanced-cli.jar patch --merge integrations.apk --patch-bundle patches.jar --signing-levels 1,2,3 official.apk
```

工具版本、补丁选择、宿主 SHA-256 和自己的签名证书必须进一步固定。现阶段不能把上游任意版本或已改包的 APK 当成合格输入；不能将所有上游增强功能默认纳入正式发布。

## 验证和交付

- [调查和方案](docs/investigation.md)
- [实际测试记录](docs/test-record.md)
- [手机安装与回滚方案](docs/install-rollback.md)
- `evidence/` 保存公开片段接口的实际返回。

`local/`、`upstream/`、APK、密钥文件均忽略。账号数据和签名私钥不放入源码 Git 历史；如需异地备份，应另行设计加密备份和密钥保管方式。

本项目代码按 GPL-3.0 提供；上游代码许可见 LICENSE。PiliPlus 仅用于研究空降接口约定，没有修改或转换其客户端代码。
