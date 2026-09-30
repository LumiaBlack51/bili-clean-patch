# 0.4.2 小火箭与付费推广过滤

在官方 9.12.0 的「去广告与空降助手」设置中新增 **屏蔽小火箭 / 付费推广** 开关，默认开启，可单独关闭。覆盖首页推荐的新旧模型和 Story 竖屏推荐流；修改后刷新首页或重新进入竖屏流。沿用此前补丁签名和设置存储。

此前规则检查 `is_ad`、广告视频类型和推荐理由「创作推广」。普通视频类型中携带商业标记、但 `is_ad=false` 的推广视频可能漏过。新规则在真实的 `AdInfo` / `FeedAdInfo` 模型上检查：

- 宿主认识的商业徽标代码 `cm_mark`：1、3、5、6、7、8。
- 宿主的自然流商业视频标记 `nature_ad=1`。
- `extra.card.ad_tag_style` 或 `ad_tag_style_full_screen`，使用宿主 `MarkLabelUtil.isShowMark` 判断有效性。Story 的商业标记可以是服务器下发的图片，不能依靠固定的小火箭资源名识别。

仅有 `ad_info`、`is_ad_loc=true`、空卡片或无效标记不足以屏蔽。未知标记代码保留；视频标题和描述不参与匹配。原有「过滤界面广告」「屏蔽创作推广」仍独立生效，所以关闭新开关后，满足原有广告规则的视频仍可被原有开关过滤。Story getter 返回过滤副本，保留缓存源列表，关闭后重新读取可恢复。

用户举报的稿件经公开详情接口核对为 `BV1j3bC6dEfC`，《最好的二人组是什么样的？》，UP 主「鱼饼不摸鱼」，时长 30 秒。见 [核对记录](../evidence/rocket-reported-video.json)。公开详情接口不包含当次推荐的推广上下文，不能据此断言该稿件始终带小火箭。补丁按每次下发的商业标记过滤，不将该 BV 或 UP 主写入永久黑名单，也不屏蔽所有类似题材视频。

验证使用单独的 AVD instrumentation，构造实际 9.12.0 Gson 首页模型、Fastjson Story 模型及旧首页模型，验证商业标记、有效图片标记、空占位、无效图片、未知代码、原有开关组合及关闭恢复。该测试包不进入交付 APK，也不安装到用户手机。受控模型验收不能等同于用户截图中两条视频的线上重现；没有商业标记的普通分发、搜索、关注和直接打开链接不在本开关覆盖范围内。

## 本地验收（2026-10-01）

- 策略测试、Gradle 编译、固定原包静态补丁及 APK v2/v3 验签通过。重复执行 `prepare.ps1`，四个内容开关均只有一份声明。
- 实际宿主进程内 [付费推广测试](../evidence/paid-0.4.2-PaidPromotionTest.txt) 30 项、[既有内容过滤测试](../evidence/paid-0.4.2-ContentFilterTest.txt) 39 项、[既有广告与暂停广告测试](../evidence/paid-0.4.2-HostModelTest.txt) 4 项、[底部设置导航测试](../evidence/paid-0.4.2-NavigationTest.txt) 4 项通过。
- [设置页操作](../evidence/paid-0.4.2-SettingsUi.txt) 4 项通过：新开关默认开启，点击可关闭，强制停止并重启宿主后仍关闭，最后恢复开启。实际设置页显示补丁 0.4.2，[截图](../evidence/paid-0.4.2-settings.png) 已检查。合计 81 项检查。
- APK：`BiliClean-0.4.2-bilibili-9.12.0-arm64.apk`；APK、`SHA256SUMS.txt` 和 `manifest.json` 见 [0.4.2 发布页](https://github.com/LumiaBlack51/bili-clean-patch/releases/tag/v0.4.2)。APK SHA-256：`2c424da2a6b4b2601ba8829f06d0ec4fc526a71e53599287fda779a94af9edfd`。
- 签名证书 SHA-256：`c57bb6b4cbf047a27e5782a5c1fc4e823beaae5f31232a79aa460b0f12d29fd8`，与此前版本相同。未向用户手机安装或清除数据。
