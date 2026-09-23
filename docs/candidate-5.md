# Candidate 5 AVD 运行记录

2026-09-23，AVD `bili-clean-api36` / `emulator-5580`，Android 16，x86_64 + ARM64 转译，游客模式。宿主为官方 9.12.0 (9120300)。输入与重签兼容处理见 native-compat.md。

产物 SHA-256：`aecf5413d819defb57d7ae78a4531514a84f611064e0304468e2c9ac38a32038`，运行代码提交 `c2a7e9c`。

## 实际通过的项目

- 启动、打开公开 UGC、竖屏/横屏播放；无登录、等级或大会员校验。
- 设置入口和四个开关正常显示，见 `candidate-5-settings-entry.png`、`candidate-5-settings.png`。
- 马督工 `BV1YpZkBDEVo` / CID `36122397322`：实际播放器显示一个广告标记，点击列出 884.079–1035.468 秒，见 `candidate-5-marker-dialog.png`。
- 自动跳过：暂停拖到 14:38，再播放跨越起点。日志记录从 884180 ms 向 1035468 ms 发起跳转；实际原版进度条显示 17:15，随后继续播放。证据是 `candidate-5-auto-skip.mp4`、`candidate-5-seek-before.png`、`candidate-5-after-auto-skip.png`、`candidate-5-post-skip-playing.png`，不是仅靠日志认定。
- 关闭自动跳过后强制停止并重新打开视频，拖到同一起点前播放，进度进入 14:49 广告段，没有跳到终点。设置持久化并生效，见 `candidate-5-auto-disabled.png`、`candidate-5-disabled-no-skip.png`。
- 切换到 `BV1zT411K7Y9` / CID `851563379`，播放器继续播放，提示“此视频暂无匹配的广告标记”，见 `candidate-5-no-markers.png` 和 runtime.log。该结果与服务空列表一致，不是跳过故障。
- `candidate-5-crash-buffer.txt` 在设置页验证后为空；仅代表该次观察窗口。

## 未通过或未验证

- 首页无广告命中日志。DEX 调查确认 9.12 使用 `PegasusResponseTypeAdapter` / `PegasusResponse` Gson 路径，旧 `PegasusParserFingerprint` 的 Fastjson 入口不足。候选 6 增加新路径，候选 5 不作为去广告验收通过版。
- 开屏、视频页其他广告、手动回看、断网、切 P 仍须分别验证。没有广告样本不能当作过滤成功。
- 当前区间是可点击提示层，不是原生进度条彩色标记。
- 除上文列出的证据外，其余候选 5 截图是操作过程记录，可能只显示控制层或系统引导，不作为成功依据。

手机未安装或清数据；PiliPlus 未修改。源码仓库为私有 `LumiaBlack51/bili-clean-patch`。

录屏第 28 秒提取为 candidate-5-skip-toast-frame.png，显示实际跳过提示。该帧处于 seek 缓冲阶段；后续落点与继续播放另由录屏和进度条截图证明。
