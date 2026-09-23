# 9.12.0 重签名运行兼容调查

2026-09-23，AVD `emulator-5580`，API 36.1，ARM64 转译。所有实验都是游客实例，没有操作手机。

| 输入 | 实际结果 |
| --- | --- |
| 官方签名原包 | host GPU 的原版基线通过；软件渲染复测连续播放 40 秒，PID 6077 不变，crash buffer 为空 |
| 仅重签原包，功能字节码未改 | 出现延迟退出，伴随 destroyed mutex 日志；不能归因于广告或空降逻辑 |
| 第四候选 | 能进入首页、取得测试视频 AID/CID，但也延迟退出 |
| 第四候选移除全局 exit hook | 同样退出；软件渲染也复现，未解决 |
| 原包重签 + 特定退出回调修改 | 连续播放 40 秒，PID 19223 不变，crash buffer 为空 |
| 第四候选 + 特定回调修改 | 能持续播放并进入原版设置；打开旧补丁设置页因 PreferenceManager final 方法冲突崩溃，尚未通过功能验收 |

## 修改范围与拒绝条件

`scripts/native-compat.ps1` 只接受 `lib/arm64-v8a/libbili.so` SHA-256：

`8e89db7d5a78e06ea97873734521e75ea80c0c925890953ba400bc397a79bf18`

ELF 首个可执行 LOAD 段的文件偏移与虚拟地址一致。`JNI_OnLoad` 的 0x8828 位置取得 0x8da0 回调地址，后续传给异步检查。该回调在参数不匹配时返回，否则等待随机 5–14 秒，再调用 `exit(0)`；0x8f14 是 sleep 调用，0x8f1c 是 exit 调用。只将 0x8da0 入口的 4 字节指令改为 ARM64 `ret`，保留函数调用者、正常 JNI 注册、请求签名和播放代码。

脚本同时核对完整库哈希与函数序言，哈希不同立即失败，不搜索猜测偏移，不把本适配应用到其他版本。输出重新进行 16 KiB 本地库对齐和 v2/v3 APK 签名。旧 `libbiliroamingx.so` 全局 exit 拦截不再使用。

这只能解决此次已定位的本地重签退出，不证明账号登录、支付、推送、其它 ABI 或 Android 版本可用。Android 安装层的证书限制仍然存在，无法直接覆盖原签名应用。

## 证据解释

- `official-native-control.mp4`、`official-native-control-after.png`、`official-native-control-errors.txt`：软件渲染下官方原包对照。
- `resign-only-errors.txt`：只重签即复现的错误。
- `native-compat-stable.mp4`、`native-compat-stable-after.png`、`native-compat-stable-errors.txt`：安装完成后独立录制的兼容修改对照。
- `native-compat-control.mp4` 开始于安装尚在进行时，只作为过程记录，不作为连续播放验收。
- `candidate-4-compat-video.png` 捕获于功能包安装完成前，仍是对照包；真正功能包截图为 `candidate-4-compat-installed-video.png`。
- `candidate-4-compat-settings.png`：原版设置页中的入口；`candidate-4-compat-settings-errors.txt`：点击后发生的偏好组件崩溃。入口存在不等于设置页通过。
