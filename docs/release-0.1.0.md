# 0.1.0 AVD 实验预览版

独立的原版客户端补丁，保留哔哩哔哩界面；没有修改 PiliPlus。实验适配官方 **9.12.0 (9120300)，ARM64，固定输入哈希**。手机现有 8.99.0 不属于支持版本。

AVD 实测通过启动、播放、无账号门槛的设置、深色模式可读性、去广告开关往返、社区标记详情、自动跳过提示及实际落点、暂停保护、手动回看、切换到无标记视频。马督工 BV1YpZkBDEVo / CID36122397322 已确认广告标记 884.079–1035.468 秒；最终包录屏中实际跨越起点后跳至 17:15 附近并继续播放。

**去广告不是全面验收通过。** 真实宿主模型的受控测试通过；线上推荐流入口已确认，但本轮线上响应未提供真实商业广告，开屏/视频页广告过滤没有实际命中证据。不能据此宣称所有界面无广告，也不能自动识别未标记的创作者广告。

完整记录见 [candidate-9.md](https://github.com/LumiaBlack51/bili-clean-patch/blob/codex/initial-patch/docs/candidate-9.md)，[安装与回滚方案](https://github.com/LumiaBlack51/bili-clean-patch/blob/codex/initial-patch/docs/install-rollback.md)。失败候选和误删规则修正也保留在仓库。

资产中的完整 APK 与 AVD 实际安装包 SHA-256 相同：`596209c6969ceec8149f92263b53093b9b8e50276dc46f09c440296304cb54a6`。`signing-key.encrypted.json` 是已做解密一致性验证的 AES-256-GCM 签名密钥备份；恢复密钥只保留本地 `local/signing-recovery-key.bin`，未上传。恢复步骤见 README。

手机未安装、卸载或清数据。没有账号数据资产：未导出手机私有数据库/cookie/令牌，普通 ADB 不能可靠读取这些内容，APK 本身也不含账号数据。本发布是可审查的实验交付，不是手机替换授权。
