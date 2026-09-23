# 9.12 推荐流适配与误删检查

候选 5 只匹配旧 `com.bilibili.pegasus.api.G` Fastjson 转换器；当前首页没有调用它。候选 6 增加 Gson 的 `PegasusResponseTypeAdapter.e`，但默认配置走反射解析，并不注册此 Adapter，同样未命中。

只读 DEX 检查最终确认 `PegasusGsonParser.g(ResponseBody):GeneralResponse` 是实际转换器，调用者为 `com.bilibili.pegasus.request.i/l`。候选 7 在此处取得 `GeneralResponse.data` 的新 `PegasusResponse` 对象，过滤其列表。开关开启/关闭均取得了真实线上日志，证明该入口实际执行。

但是候选 7 把 `ad_info != null` 当作充分广告条件。虽然日志出现 `ads=1 removed=1`，UI 也少了第一张大卡，这仍不足以证明卡片是真广告：`AdInfo` 同时有 `isAdLoc()` 和 `isAd()`，普通内容可填入广告位。**候选 7 的该计数撤回为规则命中，不能当作去广告通过证据。**

候选 8 使用 `isAd()==true` 或明确广告 `card_goto` (`ad/cm/special_s/ad_*/cm_*`) 才删除卡片。非广告但含 `ad_info` 的卡片单列为 `placeholders` 并保留。受控 Android 测试也增加这一反例，防止误删普通推荐。

实际线上命中与人工 fixture 分开保存。若当前服务只填充普通推荐，记录 `ads=0`，不伪造命中，也不通过删除普通卡片制造“去广告成功”。开屏、视频页广告覆盖还需分别确认，首页结果不能外推到所有场景。
