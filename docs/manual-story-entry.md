# 0.4.7 主动打开的竖屏视频免过滤

用户主动进入的原生竖屏播放页，现在按进入参数中的 AV/BV 编号放行当前视频。推广、小火箭、购物袋、会员购、课程及未解锁充电的内容筛选都不再删除这条入口视频。后续滑动推荐继续使用原规则；原生播放权限、付费提示和解锁要求照常生效。

## 原因与实现

原实现对 `StoryFeedResponse.Data.getItems()` 的每一条内容统一过滤，没有识别入口视频。命中过滤规则的主动播放条目也会从宿主拿到的列表中消失。这是一个可由受控模型验证的误过滤路径；用户当次闪退的完整日志没有取得，因此不将它描述为已经确认的唯一崩溃原因。

固定 9.12.0 的 `StoryVideoActivity.onCreate` 入口先读取宿主路由的 `id`、`avid`、`aid`、`bvid`；缺少独立编号时读取缓存 `story_item` 的 `player_args.aid` 或 `bvid`。仅接受正数 AV 和完整 BV 编号。放行记录以 Activity 为弱键，不落盘，并在 `onDestroy` 时清除；已结束的页面立即失效。切换或重建页面分别按自己的进入参数处理，不把后来刷到的视频当作主动点击。

使用已有的 AV/BV 转换函数将进入编号归一为同一个视频，兼容响应或缓存只携带其中一种编号的情况，并拒绝不合法的 BV。过滤时只比较条目的真实 `getAid()` / `getBvid()`，匹配后保留原对象及原顺序。不会每页保留第一条、修改原列表、关闭过滤开关、匹配标题/UP 主，或建立永久白名单。首页筛选逻辑沿用原实现。

## 验证方式

独立的 `ManualStoryEntryTest` 只在 AVD 安装，使用最终客户端的真实 Fastjson/Story 模型与安装后的 getter。覆盖各类命中项主动打开时保留、非入口命中项在前方及下一页首条仍被过滤、AV/BV/缓存编号、空/无效/不相关参数、重新进入替换身份、叠加页面分别清理、空响应及缓存源列表保留。

`live=true` 模式打开真实原生竖屏播放页，检查实际注入的 onCreate/onDestroy hook、真实媒体绑定、播放进度推进与页面保持打开。受控商业字段验证与真实视频播放分别记录，不能将它们等同于复现用户当次的线上商业视频。

## AVD 验收（2026-11-16）

最终 APK 在专用 Android 36.1 AVD 上通过 **333 项**：主动打开专项 103、真实竖屏播放与生命周期 11、购物袋 28、内容过滤 39、小火箭 30、课程 62、充电专属 27、宿主广告模型 4、开屏 25、原生底部导航 4。真实视频播放五秒后进度继续推进、页面保持打开；Activity 销毁后放行记录已从映射中删除。见 [竖屏播放截图](../evidence/manual-entry-0.4.7-portrait.png) 与 [验证汇总](../evidence/manual-entry-0.4.7-verification.json)。

首次导航测试被 AVD 后台安装 WebView 中断，ActivityManager 明确记录 `stop com.google.android.webview due to installPackageLI`，不是 Java 崩溃栈。同一 APK 重新执行后 4 项通过；首次结果和系统诊断分别保留在 `manual-entry-0.4.7-NavigationTest-initial.txt`、`manual-entry-0.4.7-webview-interruption.txt`。崩溃缓冲区没有应用崩溃记录。

核心策略三组、完整构建、重复 prepare 幂等检查、固定原包静态补丁、APK v2/v3 签名校验和 diff 检查通过。AVD 安装后的 APK 哈希与发布文件一致。沿用此前签名，可覆盖更新；本轮只在 AVD 验证，没有更新手机。

APK：`BiliClean-0.4.7-bilibili-9.12.0-arm64.apk`，SHA-256：`65c2dbe6fe00edb570a30eebf042aa38ad90871aa3ab1f3ebe120ecba885c15c`。
