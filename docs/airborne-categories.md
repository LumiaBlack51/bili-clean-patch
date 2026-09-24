# 空降助手分类、进度条与行为设置

目标为独立 `bili-clean-patch` 打包器，固定官方 9.12.0 (9120300) 输入，保留原客户端界面。PiliPlus 仅作为交互和分类颜色参考。本次未修改 PiliPlus 项目。

## 使用

- 我的 → 设置 → 去广告与空降助手：总开关、跳过提示、11 个分类的行为选择。
- 进入视频后，详情标题前显示分类标签；优先显示赞助/恰饭，其他类别以 `+N` 收起。播放器的“空降助手 · N处标记”可打开所有分类、时间和行为，也可直接进入相同的分类设置。
- 已标记区间绘制在原生进度条上，包含隐藏播放控件时的细进度条，以及展开后的横、竖屏进度条。绘制使用 View overlay，不替换播放器、原有拖动事件或控件布局。
- 设置按分类的稳定字符串键保存，返回视频立即生效；禁用分类同时隐藏对应标签、时间段和手动按钮。

| 行为 | 结果 |
| --- | --- |
| 总是跳过 | 播放进入区间就跳过，手动回到区间也会再次跳过 |
| 跳过一次 | 当前播放会话首次遇到区间时跳过，之后允许手动回看 |
| 手动跳过 | 进入区间后显示按钮，点击后跳到区间结束 |
| 仅显示 | 显示详情和进度条，不自动跳过、不弹出手动按钮；仍可在片段详情中主动跳转 |
| 禁用 | 隐藏该分类，且不自动跳过 |

升级时沿用旧“自动跳过广告”偏好作为尚未配置的赞助分类默认值：原来开启对应“跳过一次”，原来关闭对应“仅显示”。其余类别默认仅显示，不会突然开始跳过正文。重新载入播放器会重置当前会话的已跳过记录。

分类包括：赞助/恰饭、无偿/自我推广、独家访问/抢先体验、三连/互动提醒、精彩时刻/重点、开场、片尾、回顾、填充、离题、音乐中的非音乐部分。颜色分别区分，重叠片段按绘制顺序覆盖。

全片标签不会跳过整个视频；精彩时刻只提供明确的手动定位。API 的 `mute` 标记显示区间并明确标注“仅显示”，本次没有接管播放器音量。独家访问和精彩时刻的设置只提供仅显示、禁用。

## 实现与重建

- `AirborneConfig.kt`：稳定分类、颜色和持久化行为；保留旧设置的默认语义。
- `SkipEngine.java`：自动策略，区间边界、暂停保护、一次/总是、过期请求隔离。
- `CleanRuntime.kt`：读取所有支持分类，区分 `skip/full/poi/mute`，显示片段列表、手动按钮并发出真实播放器 seek。
- `AirborneUi.kt`：紧凑详情标签、原生进度条的彩色区间。无资源 ID 的细进度条为固定宿主中的 `et1.a`，已经通过实际 view tree 和原 APK 类检查定位；仅用于脚本限制的固定版本。
- `CleanSettingsFragment.kt` / `AirborneConfigDialog.kt`：设置页和视频内设置入口复用同一组控件。

构建和打包仍用 `scripts/build.ps1`、`scripts/patch-experimental.ps1`，同一签名可覆盖中午的实验版。读取社区 API 不携带账号 cookie，不提交片段或投票。API 协议参考：[空降助手 API](https://github.com/hanydd/BilibiliSponsorBlock/wiki/API)。

## 验证方法

纯 Java 策略测试：`scripts/test.ps1`。

AVD 实际播放器测试（独立 instrumentation，不会合入交付 APK）：

```powershell
./scripts/build-host-tests.ps1
adb -s emulator-5580 install --no-incremental -r local/candidate.apk
adb -s emulator-5580 install --no-incremental -r build/host-tests/host-tests.apk
adb -s emulator-5580 shell am instrument -w app.biliclean.tests/.AirbornePlaybackTest
```

测试使用真实公开视频 BV1YpZkBDEVo / CID 36122397322。此次查询有开场、自我推广、赞助、片尾四类，赞助区间为 884.079–1035.468 秒，响应保存在 `evidence/airborne-live-segments.json`。测试通过真实媒体对象读回位置，不能仅凭 seek 请求日志判定跳转完成。暂停、禁用、仅显示、手动点击、首次跳过、回看和再次跳过分别断言；测试结束恢复进入测试前的赞助分类偏好。

测试使用 SharedPreferences 调整各策略和播放器接口定位测试区间；手动跳过使用界面按钮的 click。正常设置页及原生横竖屏进度条另做 UI 验证。切换至无标记视频 BV1zT411K7Y9，验证旧分类不残留。真实社区标记会变化，测试前应复核区间。

## 最终 APK 验收（2026-09-24）

最终 APK SHA-256：`49cdb3c1d57c5509a5d6ea5e6910bd068f9dd18d71ee3d8b5bb7a272f2a3f4bc`。`emulator-5580` 上实际安装的 base.apk 已逐字节哈希核对一致，见 `evidence/airborne-installed-sha256.txt`。环境为 Android 16 / x86_64 AVD、ARM64 转译，游客模式。

| 验证 | 实际结果 / 证据 |
| --- | --- |
| 策略单元测试 | 分类策略、边界、暂停、回看、过期返回、full/poi/mute 安全性全部通过；`airborne-core-tests.txt` |
| 真实播放 | 14 个断言通过；`airborne-playback-test-final.txt`。自动首次跳过读回 1035056 ms，手动按钮跳过读回 1035468 ms；允许原生 seek 的 500 ms 落点误差 |
| 禁用与仅显示 | 播放保持在广告区间，没有自动 seek；禁用赞助分类后对应详情标签消失 |
| 手动跳过 | 按钮出现，点击后真实播放器到达区间末尾；`airborne-test-manual-before.png`、`airborne-test-manual-after.png` |
| 总是跳过 / 跳过一次 | 总是跳过在回看后再次执行；跳过一次允许回看；暂停保护与恢复播放均通过 |
| 切视频 | BV1zT411K7Y9 返回空列表，旧标签不残留；`airborne-test-unmarked.png` |
| 详情页和收起进度条 | 紧凑“赞助/恰饭 +3”标签；绿、青、蓝等区间出现在细进度条；`airborne-test-detail.png` |
| 原生控件进度条 | 竖屏和横屏展开控件均显示彩色区间；`airborne-portrait-controls.png`、`airborne-landscape-controls.png` |
| 片段与分类设置 | 四类真实区间及对应行为可读；11 类设置可滚动；赞助菜单包含五种行为；`airborne-segment-details.png`、`airborne-category-settings.png`、`airborne-behavior-options.png` |
| 正常设置入口 | 游客通过“我的”右上角设置 → 去广告与空降助手进入完整设置页；`airborne-settings-entry.png`、`airborne-settings-manual-saved.png` |
| 持久化 | 在正常设置页选择“手动跳过”，强制停止后重开视频，片段详情仍显示“手动跳过”；`airborne-persisted-mode.png` 和 `airborne-persisted.xml`。验证后恢复“跳过一次” |
| 稳定性 | 本轮 crash buffer 为空；`airborne-crash-buffer.txt` |

交付文件：`local/airborne-release/bili-clean-9.12.0-airborne-categories.apk`，同目录提供校验文件和 manifest。此次未向手机安装，未发布新的远程 release。所有截图均来自实际模拟器画面；单元测试中的人工片段不冒充真实社区数据。

## 边界

仅验证此固定 9.12.0 客户端和 AVD。没有本轮真机安装、账号/支付/投屏验收；本次不改变已有去广告过滤范围。社区没有标记的广告仍无法识别。分类配色目前固定；片段提交、投票和自动静音不在本次实现范围内。
