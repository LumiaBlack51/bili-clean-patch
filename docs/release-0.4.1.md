# 0.4.1 官方哔哩哔哩播完关闭修复

目标是固定哈希的官方哔哩哔哩 9.12.0（包名 `tv.danmaku.bili`，版本码 9120300），保留 0.4.0 的同一补丁签名与应用数据。没有修改竖屏播放页的快捷定时按钮。

## 真机复现与修复

在普通视频详情页，从原生“定时关闭”选择“播放到当前视频结束”后，界面显示已设置提示。视频实际结束时，当前详情页会关闭；若任务栈中还有先前打开的视频详情页，它会重新出现在屏幕上，看起来像定时关闭没有生效。真机复现时确实观察到此情形。

0.4.1 在当前视频完成时暂停播放器并结束哔哩哔哩当前任务栈，避免旧详情页恢复播放。播放器或 Activity 在同一视频中转移时，定时状态跟随当前播放器；明确切换到另一视频时取消旧定时。若播放器尚未准备好，点击菜单时会提示稍后重试，避免静默失败。补丁版本提升为 0.4.1，以便手机中的 0.4.0 从应用内检测到更新。

## 本地候选验收

- 输入包：固定 SHA-256 `b9c62efed1452c21a070919a9d937428ab6b7308a4d9e066d282392f50333f31` 的官方 9.12.0 原包。
- 候选 APK SHA-256：`d73b62b1d462730e084cbf2dc20cbab0348d40a612d944b873f2dad5ae4eb20d`。
- 候选证书 SHA-256：`c57bb6b4cbf047a27e5782a5c1fc4e823beaae5f31232a79aa460b0f12d29fd8`，与手机中的 0.4.0 相同。APK v2/v3 验签通过。
- `scripts/build.ps1`、`scripts/build-host-tests.ps1`、`scripts/patch-experimental.ps1` 通过。
- Android 36.1 专用 AVD 安装候选后，[GlobalTimerTest](../evidence/timer-0.4.1-GlobalTimerTest.txt) 11 项全部通过，包含暂停等待、实际播放完成和关闭；[StoryPlaybackTest](../evidence/timer-0.4.1-StoryPlaybackTest.txt) 13 项全部通过，包含 Story 到详情页迁移后的播完关闭。
- 完整 [PlaybackControlsTest 第一次](../evidence/timer-0.4.1-PlaybackControlsTest-attempt1.txt) 在横屏单击显示中央按钮处失败；[第二次](../evidence/timer-0.4.1-PlaybackControlsTest-attempt2.txt) 在受控播放列表的下一集按钮点击处失败。手势主路径及迁移测试通过，但不能将完整播放器回归记为通过。

## 真机应用内更新

待 0.4.1 Release 发布后记录。手机不会通过 ADB 安装候选包。
