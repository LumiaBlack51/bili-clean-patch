# 手动按钮与暂停广告修正（2026-09-24）

## 问题与修改

旧版对 `actionType=mute` 的有起止时间标记只画进度条，即使对应分类选择手动跳过，也没有按钮。现在手动模式覆盖 `skip` 和 `mute` 区间；静音标记仍不会自动跳过或自动静音。全片标签和单点精彩时刻不作为可跳过区间。

旧版仅在按钮变量为空时创建 View，宿主移除浮层后会留下非空但已脱离界面的引用。现在每次显示时检查当前内容根节点并重新挂载，提升按钮层级；详情标题或进度条绘制异常不再阻止按钮更新。点击时重新核对当前视频、播放器、开关、分类模式和时段，避免旧按钮误跳。

用户提供了《睡前消息》1047 期，已确认 `BV1DuZFB8EZi / CID 37848154233`。社区响应见 `evidence/1047-live-segments.json`：开场 0–8.027 秒、赞助 204.911–298.888 秒、片尾 1077.278–1101.12 秒，三段均为 `skip`，因此静音标记遗漏不是此样本的原因。浮层被移除的失败是在受控测试中重现，不冒充自然播放过程中捕获的故障。

## 暂停广告

0.2.0 没有专门拦截 9.12.0 的暂停广告入口，不能声称已支持。本次增加 `Clean pause ads` 静态补丁，受“过滤界面广告”开关控制，在以下宿主方法入口返回无广告结果：

- `VDPausedPage.requestPausedPage`：暂停广告请求。
- `VDPausedPage.decodeViewEndPagePausedPage`：预载暂停广告数据解析。

核对固定原包签名、完整方法参数和返回类型，匹配失败则打包失败。宿主调用方已有空结果分支；开关关闭时继续执行原方法。只改变广告入口，不修改播放器暂停、恢复和普通控件。切换开关后重新进入视频可避免旧场景中已缓存的数据。

测试区分：宿主方法的受控调用可以验证已安装包的拦截与开关；游客暂停时没有出现广告，不能单独作为“真实广告被拦截”的证据，也不代表覆盖所有番剧、直播或其他广告形态。

## 验证方式

- `scripts/test.ps1`：九类区间的手动模式、静音区间、边界以及 full/poi/禁用/仅显示的安全性。
- `AirbornePlaybackTest`：真实公开视频、原生全屏按钮、真实触摸手动按钮，并在暂停状态下读回播放器位置，排除自然播放到目标造成的假通过。
- 受控浮层移除后重新挂载、受控 mute 标记，单独标注，不视为社区实测样本。
- `HostModelTest`：直接调用安装后的暂停广告入口，检查空结果与开关关闭时放行，同时回归推荐流广告过滤。

所有 Android 验证仅在任务专用 `emulator-5580` 中执行；独立 instrumentation 不合入交付 APK，不安装到手机。

## 最终结果

- 1047 期完整回归 **20 项 PASS**：`evidence/1047-final-playback-tests.txt`。包含开场/片尾的横竖屏按钮、真实触摸后的实际播放位置，以及自动/手动/禁用/仅显示、回看、暂停恢复和切视频清理。
- 开场读回 8026 ms（浮点换算比服务标记少 1 ms）、片尾读回 1101120 ms；触摸时暂停播放，排除了自然播放到目标导致的假通过。
- 受控浮层移除：旧版失败 `Manual button lost after host detaches overlay`，新版恢复并可点击；分别见 `evidence/manual-detach-baseline.txt` 与最终回归记录。
- 旧版 1047 期正常界面的对照 **18 项 PASS**（`evidence/1047-baseline.txt`），未稳定复现用户手机上的自然触发路径。本次修复不意味着已确认手机上缺失按钮的唯一原因。
- 暂停广告：安装后的两个宿主入口均返回无广告结果，开关关闭时 guard 放行；`evidence/manual-pause-host-tests.txt`。未取得服务器实际投放的暂停广告素材，不能宣称所有暂停广告形态均完成线上验收。
- `evidence/manual-pause-crash.txt` 为 0 字节；`evidence/manual-pause-runtime.txt` 保存实际跳转与受控广告入口日志。
- 独立测试包已卸载，测试修改的分类偏好已恢复；手机未改动。

截图：`evidence/1047-portrait-intro.png`、`1047-portrait-outro.png`、`1047-landscape-intro.png`、`1047-landscape-outro.png`。另有明确标注的 `1047-portrait-reattach.png` 与 `1047-controlled-mute.png`，后两者为受控场景。

交付 APK：`bili-clean-9.12.0-manual-pause-fix.apk`，SHA-256 为 `674cb398f8c13be1b2c628663a963bf235749f7cd461cf35b3f98c9f35996b97`。已与 AVD 实际安装的 base.apk 核对一致（`evidence/manual-pause-installed-sha256.txt`），沿用上一版签名，v2/v3 校验通过。

[GitHub 0.2.1 发布页](https://github.com/LumiaBlack51/bili-clean-patch/releases/tag/v0.2.1-manual-pause-preview)
