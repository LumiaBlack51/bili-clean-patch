# Candidate 9：交付实验版的实际测试

2026-09-23；源码运行部分提交 `55f002e`。目标官方 9.12.0 (9120300)，ARM64；AVD `bili-clean-api36` / `emulator-5580`，Android 16，x86_64 的 ARM64 转译，3 GB、4 核、host GPU、Vulkan 关闭。全程游客模式。

APK SHA-256：`596209c6969ceec8149f92263b53093b9b8e50276dc46f09c440296304cb54a6`。AVD 实际安装文件的 SHA-256 与交付文件完全相同，见 `candidate-9-installed-sha256.txt`。证书 SHA-256：`c57bb6b4cbf047a27e5782a5c1fc4e823beaae5f31232a79aa460b0f12d29fd8`。

## 通过的 AVD 运行项目

| 项目 | 实际观察 | evidence/ 证据 |
| --- | --- | --- |
| 启动、播放 | 原版界面、公开 UGC 正常播放，连续经历暂停、seek、回看、切视频 | video-start、auto-skip.mp4、manual-rewatch、no-markers 截图 |
| 无账号门槛、深色设置 | 游客打开设置；四个开关及说明可读 | candidate-9-dark-settings.png |
| 去广告开关往返 | 关闭再开启，等待后台清理完成；PID 4042 不变，崩溃缓冲为空 | candidate-9-ad-toggle-off/on.png、toggle-process.json、toggle-crash-buffer.txt |
| 标记显示 | BV1YpZkBDEVo / CID 36122397322，显示 1 段及准确区间 884.079–1035.468 秒 | candidate-9-marker-details.png |
| 暂停保护 | 暂停拖入 15:14，等待 6 秒仍在该位置，没有 seek 日志 | candidate-9-paused-inside-marker.png、paused-runtime.log |
| 自动跳过 | 再拖到 14:38，恢复播放；884237 ms 触发到 1035468 ms，实际录屏显示提示，原版进度条随后到 17:18 且继续播放 | candidate-9-auto-skip.mp4、before-auto-skip.png、after-auto-skip.png、skip-toast-frame.png |
| 手动回看 | 成功跳过后拖回 15:05，正常播放，不再次自动跳走 | candidate-9-manual-rewatch.png、rewatch-runtime.log（仍只有一次 seek） |
| 切换到无标记视频 | BV1zT411K7Y9 / CID 851563379 正常播放，显示暂无匹配广告标记；未沿用上一视频区间 | candidate-9-no-markers.png、live-runtime.log |
| 观察窗口稳定性 | 上述操作后 crash buffer 为空 | candidate-9-crash-buffer.txt |

录屏第 18 秒提取为 `candidate-9-skip-toast-frame.png`。截图提取未修改画面内容。原片自带约 2.5 分钟广告段，跳过验证先查询了真实社区标记；没有用无标记视频冒充自动跳过故障。关闭自动跳过并重启后的播放测试另见 candidate-5.md（同一跳过策略，进入 14:49 未跳走）。

## 去广告：通过范围和未完成部分

独立 Android instrumentation 在同一宿主进程使用真实 Gson、真实卡片和响应模型。四张人工 fixture 中，`card_goto=cm` 和 `is_ad=true` 两张被删除；普通卡和 `is_ad=false` 的广告位填充保留；关闭开关时四张全部保留。`candidate-9-host-model-tests.txt` 为 PASS，`candidate-9-controlled-fixture-runtime.log` 是单独的人工样本日志。测试包已卸载，不包含在交付 APK 中。

线上推荐流入口确实运行，候选 8 多次刷新和候选 9 的响应均记录 `ads=0`，普通填充正确保留。**没有取得可验证的线上商业广告样本，不能宣称线上去广告全面验收通过。** 候选 7 的 `ad_info!=null` 误删规则已修复，它的 removed=1 不能作为成功证据，详见 feed-investigation.md。

开屏和视频页广告过滤有适配代码，但此次没有实际广告命中证据。当前实验版不承诺全界面无广告。没有社区标记的创作者广告也不能自动识别。

## 支持边界和已知问题

- 实验适配仅限上述官方 9.12.0 的固定 APK 哈希；其他版本拒绝构建。手机上的已改包 8.99.0 未支持。
- 已验证环境仅此 AVD；未验证物理 ARM64 手机、其他 Android 版本、登录、支付、下载、投屏或长时间后台播放。
- 区间通过可点击提示层展示，尚未画到原生进度条上。
- 网络错误有独立处理代码，但断网恢复、快速并发切换、多 P、预加载等复杂情形尚未做完整运行验收；失败请求需重新载入视频。
- 已跳过片段允许回看；重载视频会重置本轮跳过状态。
- 去广告开关只清理旧 splash JSON 文件，不再调用未适配的 BLKV 偏好缓存接口；其他已有开屏缓存未保证清除。
- 原版重新签名会触发延迟退出，使用固定库哈希守卫的局部兼容修改，见 native-compat.md。

## 交付判断

提供 **AVD 实验预览版**，不是“全部功能和广告场景已验收”的稳定版。源码、构建说明、实际证据和完整 APK 可供审查；因广告真实样本验收仍有缺口，没有进入手机替换阶段。手机应用、账号和下载数据均未改动。
