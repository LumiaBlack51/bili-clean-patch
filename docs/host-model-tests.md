# 实际宿主模型的受控测试

`tests/android/HostModelTest.java` 是单独的 Android instrumentation，不进入补丁 APK。它使用已安装候选客户端的类加载器，调用客户端真实 Gson、9.12 的 `Fs0.v` 卡片类、`PegasusResponse` 和本项目过滤函数。

测试四张人工卡片：普通视频、`is_ad=false` 的广告位普通填充、`card_goto=cm` 广告、`ad_info.is_ad=true` 广告。首先验证 Gson 的真实 JSON 注解确实填充正确字段，再检查开启过滤时保留前两个相同对象，关闭时四张均保留。测试仅临时切换进程内设置值，结束恢复原值，不写入持久化设置。

这能发现反射方法、混淆字段、JSON 注解、不可变集合或设置适配错误；**不能证明线上广告响应已被拦截，也不能代替 UI/播放验收**。测试结果必须和 live feed 日志分别解释。

仅在任务专用 AVD 安装同一签名的候选后执行：

```powershell
./scripts/build-host-tests.ps1
adb -s emulator-5580 install --no-incremental -r ./build/host-tests/host-tests.apk
adb -s emulator-5580 shell am instrument -w app.biliclean.tests/app.biliclean.tests.HostModelTest
adb -s emulator-5580 uninstall app.biliclean.tests
```

原版或其他签名不能运行该 instrumentation。构建脚本从本次 integrations 的 R8 mapping 自动生成测试用类名和字段名，所以必须和对应的候选 APK 一起构建。测试包不应安装到手机。输出 `PASS controlled host-model fixtures` 才代表此项通过；编译成功不代表测试运行成功。
