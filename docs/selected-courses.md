# 0.4.3 竖屏课程、未解锁充电专属与指定课程过滤

## 小火箭漏屏蔽排查

2026-10-01 只读检查连接手机的已安装原版补丁：`base.apk` SHA-256 为 `d73b62b1d462730e084cbf2dc20cbab0348d40a612d944b873f2dad5ae4eb20d`，与已发布 0.4.1 完全一致；安装包 DEX 的 `CleanUpdate` 也包含 0.4.1。手机尚未升级到新增小火箭 / 付费推广识别的 0.4.2。旧版广告和创作推广规则仍能过滤部分此类视频，所以“大多数已经过滤”不代表新版开关已生效。见 [安装版本证据](../evidence/phone-installed-filter-version.json)。

截图中是 UP 主「咕嘎冒险王」的《方舟孤岛21》，标题附近可见小火箭。没有这条视频的分享链接或当次推荐响应，不能断言 0.4.2 规则必然漏过它，也不能用公开视频详情替代推荐商业标记。本次保留 0.4.2 的小火箭识别，随 0.4.3 一起交付。

## 所有竖屏课程

设置 → 去广告与空降助手新增 **屏蔽竖屏课程**，默认开启，可独立关闭。直接沿用 9.12.0 的 `StoryDetail.isCheese()` 分类（`goto=vertical_course`），过滤竖屏流中的所有课程，包括收费、免费、已购和试听；无需课程编号，也不依赖是否携带广告标记。首页不受这个开关影响。修改后重新进入竖屏流。

关闭后其他开关继续独立生效：两门指定课程可能仍被「屏蔽指定课程」过滤；带推广标记的课程仍可能被广告 / 小火箭开关过滤。普通 UGC、番剧、直播以及标题写有“课程”的普通视频不因为这个开关被删除。

## 未解锁充电专属

新增 **屏蔽未解锁充电专属**，默认开启，可独立关闭，只作用于 Story 竖屏推荐。读取原版 9.12.0 的 `upower_info`，有充电试听标记 `is_preview=true` 或付费墙 `show_paywall=true` 时过滤；仅有充电链接、标题、空对象或付费墙描述对象不作为未解锁依据。

保留 `is_unlocked=true` 的视频，即使同时有过期的试听或付费墙标记。沿用宿主 `StoryChargeBarState` 的完整解锁判断：`duration>0` 且 `watch_time_length>=duration*1000` 时保留；`duration` 为秒，观看额度为毫秒，使用 Long 避免溢出。无试听 / 付费墙的已解锁充电视频也保留。`is_iaa=true` 是宿主另行处理的广告解锁内容，本开关不将其视为充电专属。

过滤时保留缓存源列表；关闭开关或缓存模型变为已解锁后，getter 可重新返回相应视频。不绕过付费墙、不修改权限。未知状态保留。其他广告开关仍可独立过滤带商业标记的视频。

## 两门指定课程

设置 → 去广告与空降助手新增 **屏蔽指定课程**，默认开启，可独立关闭。覆盖首页推荐的新旧模型及 Story 竖屏推荐，不阻止用户通过搜索、收藏或链接主动打开课程。修改后刷新首页或重新进入竖屏流。

只过滤用户截图指定的课程和其课时 / 试听推荐：

- [聂辉华教授：基层中国的运行逻辑](https://www.bilibili.com/cheese/play/ss5526)，课程编号 `5526`。
- [陆铭教授：中国经济治理、结构与增长](https://www.bilibili.com/cheese/play/ss142392442)，课程编号 `142392442`。

课程身份和完整课时清单经公开 `pugv/view/web/season` 接口核对，保存为 [课程证据](../evidence/selected-courses.json)。两个列表共 44 项（包括导论 / 导语），对应页面的 22 和 20 课时另加各一项导入课。

按照固定原版 9.12.0 的原生课程路由识别 `bilibili://cheese/season/{season_id}`、`bilibili://cheese/season/ep/{epid}` 及 `www/m.bilibili.com/cheese/play/ss…` / `ep…`。Story 同时读取 `course_info.desc_detail_uri`；`player_args.season_id` 和 `ep_id` 只在 `vertical_course` 类型下使用，避免与番剧编号混淆。已核对的课时 AV 编号也能识别以普通视频类型推荐的试听片段。

标题、UP 主名、经济学主题、任意 URL 查询参数不是匹配条件。其他课程、同作者普通视频和讨论相同主题的视频保持原有行为。关闭开关后，新首页响应和缓存的 Story 源列表可恢复课程推荐；其他广告开关仍可独立命中带广告的课程。

## 验证

使用独立 AVD 的真实宿主 Gson / Fastjson 模型和已打补丁的 Story getter。`CourseFilterTest` 检查所有竖屏课程分类（收费、免费、已购、试听及无课程详情对象）、对首页的隔离、关闭后缓存恢复，以及两门课的课程 / 课时路由、课时 AV、竖屏课程参数、混合推荐流。反例包含同编号番剧、同作者其他课程、相似标题、邻近编号、外部域名、普通视频查询参数、普通 UGC 和直播。测试 instrumentation 不进入交付 APK，也不安装到用户手机。

`UpowerFilterTest` 使用真实 Fastjson 模型和安装后的 Story getter，覆盖明确未解锁、试听、付费墙、完整额度的边界和长整数、已解锁与过期标记冲突、非试听充电视频、空字段、普通标题、广告解锁类型、开关恢复、缓存视频解锁后的恢复，以及首页不受影响。

2026-10-01 最终候选构建、核心策略测试、静态补丁和 APK v2/v3 验签通过。候选 SHA-256：`a1579d1ed1f5d1476f2881723e7e9cbacb6a26c9ffad30edbb3a9c8864b74b63`；证书 SHA-256：`c57bb6b4cbf047a27e5782a5c1fc4e823beaae5f31232a79aa460b0f12d29fd8`，与已发布补丁相同。

专用 Android 36.1 AVD 安装最终候选后的检查全部通过：

| 检查 | 通过项数 |
| --- | ---: |
| [CourseFilterTest](../evidence/courses-0.4.3-CourseFilterTest.txt) | 62 |
| [UpowerFilterTest](../evidence/courses-0.4.3-UpowerFilterTest.txt) | 27 |
| [PaidPromotionTest](../evidence/courses-0.4.3-PaidPromotionTest.txt) | 30 |
| [ContentFilterTest](../evidence/courses-0.4.3-ContentFilterTest.txt) | 39 |
| [HostModelTest](../evidence/courses-0.4.3-HostModelTest.txt) | 4 |
| [NavigationTest](../evidence/courses-0.4.3-NavigationTest.txt) | 4 |
| [实际设置页](../evidence/courses-0.4.3-SettingsUi.txt)，默认值、关闭、重启保存、恢复 | 10 |

小米 13 Pro（2210132C）在用户明确授权后替换原版客户端，先安装已通过课程测试的版本，随后覆盖更新最终 0.4.3。手机已安装 `base.apk` 哈希与最终候选一致；正常启动，补丁设置显示 0.4.3，三个新开关和小火箭开关均开启；充电过滤关闭后重启仍保存，验证后恢复开启。[真机记录](../evidence/xiaomi-0.4.3-smoke.txt) 共 10 项通过，[最终设置截图](../evidence/xiaomi-0.4.3-final-charging-settings.png)。未向手机安装测试 instrumentation，也未导出账号数据。此次真机在未登录状态下检查了六屏首页推荐，未找到原生 Story 卡片，因此未记录实际竖屏刷视频为通过。

线上截图中的三条推荐及一对已解锁 / 未解锁充电专属视频尚未在实时推荐流重现；过滤语义由上述真实宿主模型测试验证。充电模型的只读调查摘要见 [字段证据](../evidence/charging-9.12.0-model.json)。
