# 实际测试记录

日期：2026-09-23。**实验适配 9.12.0 (9120300)；完整广告场景验收未完成。最新交付结论见 [候选 9](candidate-9.md)，下表保留调查过程，不能将早期结果当作最终状态。**

| 项目 | 结果 | 证据 / 边界 |
| --- | --- | --- |
| 备份 APK 识别 | 通过 | 8.99.0；BiliRoamingX 签名；并非官方包 |
| 手机包版本读取 | 通过 | 只读 dumpsys package，未安装修改 |
| AVD 创建 | 通过 | API 36.1 x86_64，WHPX 可用 |
| 原版安装 | 通过 | 官方 9.12.0，`adb -s emulator-5580 install` 返回 Success |
| 原版启动 / 播放 | 复测通过 | 首次在并行构建、低内存下 ANR；单独运行 AVD（3 GB、4 核、host GPU、禁用 Vulkan）后进入首页并播放所选马督工视频；保存截图及 20 秒录屏 |
| 跳过策略单元测试 | 通过 | `scripts/test.ps1`，时间边界、暂停/禁用、回看、过期响应、视频时长、重置、无效范围 |
| Gradle 配置 / 完整构建 | 通过 | 公开 CLI 引擎依赖、NDK 28 兼容修复后 `dist` 成功；不能据此宣称运行兼容 |
| 9.12.0 补丁适配检查 | 第二轮全部选定补丁匹配 | 第一轮有五项旧补丁失败及寄存器错误，已保留日志；第二轮改用独立 Clean 补丁，仍待安装验收 |
| 广告过滤验证 | 候选 9 受控模型通过，线上真实广告待样本 | 实际 Gson 入口已命中；保留非广告填充，不能把候选 7 误删计数当成功证据 |
| 标记数据预检查 | 通过 | 下列真实 HTTP 响应 |
| 客户端标记显示 / 自动跳过 | 候选 5、9 通过 | 区间详情、录屏中的跳过提示、14:38 → 17:15 与后续播放；见 [候选 5 记录](candidate-5.md) |
| 首个完整候选安装 / 启动 | 安装通过，启动失败 | `candidate-startup-crash-1.txt`：旧框架番剧搜索注入访问私有字段；已修改源码移除该启动功能，待重测 |
| 第二个完整候选安装 / 启动 | 安装通过，启动失败 | 已越过第一个崩溃点，接受游客协议后旧 JSON 钩子引用不存在的 SplashData；`candidate-2-start-errors.txt` 和 `candidate-2-result.json` 记录错误及 APK 指纹 |
| 第三个完整候选安装 / 启动 | 安装通过，启动失败 | `candidate-3-errors.txt`：上游 Unlock ProtoBuf 将 BroadcastEvent.setShared 改成 virtual 后未匹配调用指令；已从选择列表移除该无关补丁，下一候选待测 |
| 第四候选 / 重签名对照 | 定位到延迟退出；局部兼容修改后播放恢复 | 详见 [本地兼容调查](native-compat.md)，含只重签原包的对照，不能把最初播放数秒算作通过 |
| 第四候选设置 | 入口可见，打开失败 | 原版设置页顶部有入口；旧 PreferenceManager 的 final 方法冲突已记录，改用独立轻量设置页后待复测 |
| 手机安装测试 | 未执行 | 需 AVD 通过后另选安装方案 |

## 已确认的马督工样本

公开视频：[睡前消息1019，第五现代化还是 AI 后现代？](https://www.bilibili.com/video/BV1YpZkBDEVo/)

- BV：`BV1YpZkBDEVo`
- CID：`36122397322`
- `sponsor / skip`：884.079–1035.468 秒
- UUID：`bd8ce8f7bf80d9eae6a2563cc2b793ca78eaa4679a6479162547f6f5aa7486ca7`
- 时长字段：1974 秒；同响应其他类别有约 1973.56 秒。
- 实际查询时间：2026-09-23 12:41:48 +08:00，HTTP 200。
- 完整响应：`evidence/BV1YpZkBDEVo-segments.json`。

`BV1tWvDBkEbb` 有片头/片尾但没有 sponsor；不能用于广告自动跳过验收。`BV1zT411K7Y9` 返回 HTTP 200 空列表；记录为“无标记”，不是功能故障。B 站网页视频元数据 API 对这三条请求返回 HTTP 412，没有伪造成功元数据。

## 验收清单（分项最新结果见上表及候选记录）

1. 验证官方 APK 下载完整性、签名、版本，AVD 安装后保留启动和连续播放证据。
2. 固定匹配版本的原包，构建补丁 APK；保存依赖和输出 SHA-256。
3. AVD 保存原版快照，安装重签版本，确认设置无需登录且可见。
4. 对照开屏、推荐流和视频页广告。无广告样本单次展示不能证明去广告成功；必须记录过滤命中或有广告的原版对照。
5. 再查询该 BV 的 CID/广告标记；在原版播放器播放至约 880 秒，录制标记提示、跨过 884.079 秒时的跳转、落点约 1035.468 秒及继续播放。另测自动跳过关闭、暂停、手动回看、切 P、切视频与网络失败。
6. 用实际播放位置确认落点，不能以 `seek-request`、开关开启、编译成功或接口有数据代替播放器证据。

## 已知问题

- 官方 latest 已确认是 9.12.0（9120300），作为当前适配候选。原版 8.99.0 未获取，手机版本仍未支持。
- 旧通用补丁确实不能直接用于 9.12.0；第二轮收窄补丁选择并增加版本检查。
- 旧 JSON 通用钩子已换为确切模型过滤，候选 5 启动/播放正常；开屏过滤命中仍待取得实际样本。
- 候选 5 已改用 PlayViewUniteReply.playArc 识别 UGC，并验证所选视频及切换到无标记视频；不依赖旧 VideoInfoHolder。复杂预加载、快速并发切换、切 P 尚未完整验证。
- 当前标记是可点击区间提示层，不是已适配原生进度条的彩色区间。
- 网络失败本视频暂不自动重试；需要重新载入视频。
- 仅覆盖已接入的广告过滤位置，不能宣称“全部界面无广告”。
- 未标记的创作者广告不能自动识别；社区标记本身也可能不准确。

## 原版证据

`official-package.txt`、`official-signature.txt`、`official-sha256.txt` 记录实际输入包。`official-first-launch.png` 是首次协议界面；`official-video-open.png` 和 `official-startup-errors.txt` 记录首次 ANR。复测的 `official-retest-main.png`、`official-marked-video.png`、`official-playback-time.png` 显示原版首页及不同播放画面；`official-playback.mp4` 是实际 20 秒连续录屏。

这些证据仅证明原版基线，不证明补丁版通过。

## 0.4.4 后台返回开屏缓存修复（2026-10-02）

用户报告：退回桌面再进入 B 站，偶尔再次出现开屏广告。连接手机为 OPPO PLQ110，原安装包与 0.4.3 发布 APK 的 SHA-256 一致，且「过滤界面广告」开启。

原有 `CleanJson` 只清理旧模型的 JSON 广告列表；设置变更回调只删除 `splash2/splash.json`，无法覆盖已选中/预载的内存素材。固定原版 9.12.0 中还存在两套开屏实现：

- 旧 `SplashManager.b` 可以直接返回 `Wo1.b.b` 中的预载素材；`SplashManager.a` 根据已选中的素材创建广告页，恢复的 `HotSplashActivity` 也调用它。
- KSplash 的 `BusinessSplashViewModel.c` 可直接返回 StateFlow 中准备好的热启动素材，`a` 负责冷热启动素材选择；这条路径不经过旧 JSON 模型过滤。
- R8 共用回调 `bf.d.invoke` 的开屏分支会选择其中一套热启动路径。新补丁只在该分支的 `ip1.a.a()` 特性判断之前插入拦截，保留同一方法中的其他分支。

`CleanSplashPatch` 在上述四个素材/页面入口和一个热启动分支检查 `CleanAds`，开启时使用宿主既有的“无广告”空返回或返回 `Unit`，不读取缓存、不创建广告页、不等待热启动素材。关闭时继续原来的指令。匹配固定类、完整签名、静态标志及分支布局；不支持的宿主结构使补丁构建失败。

### 对照与回归

独立模拟器保留 0.4.3，包哈希为 `a1579d1ed1f5d1476f2881723e7e9cbacb6a26c9ffad30edbb3a9c8864b74b63`。`SplashEntryTest -e baseline true` 在广告开关开启时向实际宿主缓存放入受控素材，确认旧版仍从两套缓存返回这些对象。证据：[旧版对照](../evidence/splash-entry-baseline-0.4.3.txt)。

同一模拟器更新到 0.4.4 后，同一缓存测试通过 25 项检查：旧素材在 COLD/HOT/CALL_UP 来源均被拦截，已选中的旧素材不能创建页面，KSplash 热启动缓存被拦截，选择入口先于原逻辑返回，共用热启动分支先于路由/等待返回。反复关闭/开启过滤，关闭时两套缓存可继续返回原对象，KSplash 缓存对象本身没有被改写。测试结束恢复测试开关和注入缓存。证据：[新版测试](../evidence/splash-entry-0.4.4.txt)。

| 回归套件 | 通过检查数 |
| --- | ---: |
| HostModelTest（含暂停广告） | 4 |
| ContentFilterTest | 39 |
| PaidPromotionTest | 30 |
| CourseFilterTest | 62 |
| UpowerFilterTest | 27 |
| NavigationTest | 4 |

共 191 项新入口与广告/内容/导航检查通过；`scripts/test.ps1` 核心空降测试及完整 Gradle 构建通过。独立测试 APK 只装在模拟器，不包含在交付 APK，也未安装到手机。

### 手机验证与交付

使用同证书 `adb install -r --no-incremental` 覆盖更新 OPPO，未卸载或清理应用数据。安装后手机 APK 哈希与产物一致；设置页显示「当前补丁 0.4.4」且原广告开关仍开启。

- 冷启动进入首页正常。
- 首页退回桌面，分别停留 3、15、45 秒，通过桌面启动入口返回，均正常显示原首页，日志出现 `splash-entry blocked`，未观察到开屏广告或目标进程的 `FATAL EXCEPTION`。
- 设置页退回桌面，通过系统最近任务中的 B 站卡片返回，原设置页保留，未观察到开屏广告；可继续进入补丁设置。
- 手机界面与日志留在忽略的 `local/`；只保存不含账号数据的验证汇总到 [手机返回汇总](../evidence/splash-phone-return-0.4.4.json)。

交付：`BiliClean-0.4.4-bilibili-9.12.0-arm64.apk`、`manifest.json`、`SHA256SUMS.txt`，保存于本地 `local/release-0.4.4/` 和 [GitHub 0.4.4 发布页](https://github.com/LumiaBlack51/bili-clean-patch/releases/tag/v0.4.4)。APK SHA-256：`d79b5e304cf7d4d00b7d09866ea52744009ecab1edd1f7958b7e6ae4d99660a5`；证书 SHA-256 仍为 `c57bb6b4cbf047a27e5782a5c1fc4e823beaae5f31232a79aa460b0f12d29fd8`。

边界：缓存对照使用受控宿主对象，未捕获用户此前偶发的线上广告素材；短时真机返回测试不能证明所有投放类型、后台时长或后续宿主版本均已覆盖。本次证据证明已适配的两套缓存开屏入口会被拦截。
