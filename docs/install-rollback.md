# 手机安装与回滚方案（2026-09-23 真机核对）

现在没有执行手机安装、卸载、清数据或账号导出。此文档是方案，不是已授权覆盖的执行指令。

## 现状

手机 OPPO PLQ110，包名 `tv.danmaku.bili`，8.99.0 / 8990400，arm64-v8a。已从当前手机重新提取唯一的 base.apk（pm path 未返回拆分包），签名为 BiliRoamingX；新实验包使用不同签名。Android 常规安装不能以不同证书覆盖同包名应用。

可审查的包清单：

| 用途 | 本地文件 / 版本 | SHA-256 |
| --- | --- | --- |
| 现有回滚候选 | `E:\software\bili-airborne\installed-backup.apk`，8.99.0 | `a8784d0622f49357857d4a9fd75df1a90c19b0aee31cc30e87a846cdf9672fc5` |
| 新实验候选 | `E:\software\bili-clean-patch\local\candidate.apk`，9.12.0 / candidate 9 | `596209c6969ceec8149f92263b53093b9b8e50276dc46f09c440296304cb54a6` |

现有备份证书指纹 `4ac19c0edb79427fa4f31b71dc32f362336e6f5e56b1fdfcde639b30176a7f08`；新实验包证书指纹 `c57bb6b4cbf047a27e5782a5c1fc4e823beaae5f31232a79aa460b0f12d29fd8`。两包均通过 apksigner verify。新提取的 `E:\software\bili-clean-patch\local\phone-backup-20260923\base.apk` 为 198811834 字节，SHA-256 与上述旧备份完全一致。回滚候选并未现场恢复到手机验收；不能把“有 APK”当作账号与私有数据已备份。

目前空降和播放的 AVD 验证通过，线上广告场景仍有缺口。用户已要求安装真机；现已完成只读核对及原包备份，等待用户按此前要求选择是否接受卸载造成的数据丢失。尚未执行卸载或安装，尚未导出账号、设置或下载数据。

## 方案 A：保留手机现状（默认）

只在 AVD 使用独立测试实例。保留手机应用、登录和下载数据，不承诺尚未验证的并行包名方案可用。

## 方案 B：同包名替换（会有数据丢失风险）

前提：用户审查并明确接受数据丢失后选择。当前实验版的 AVD 验收范围及缺口见 candidate-9.md；上述手机版本、证书及完整安装包清单已核对。

1. 如需保留设置或下载，先通过应用公开功能导出并验证恢复，再执行替换。目前没有已验证的数据备份，不承诺任何应用数据可恢复。
2. 说明 Android 沙箱内账号数据库、登录令牌和缓存通常不能通过普通 ADB 完整备份；旧式 adb backup 不能当作可靠方案。原 APK 不包含账号数据。
3. 确认用户能重新登录，接受不可备份内容丢失后，才卸载旧包并安装测试过的新包。
4. 回滚需卸载新签名版本，再安装原签名完整原包；恢复已验证的设置备份、重新登录。**回滚安装包不等于恢复原私有数据。**

选定替换后的具体操作仅针对已连接的 OPPO：

```powershell
adb -s 3B162800PCJ00000 uninstall tv.danmaku.bili
adb -s 3B162800PCJ00000 install --no-incremental E:\software\bili-clean-patch\local\candidate.apk
```

随后核对安装版本和包哈希，检查启动、设置、公开视频播放与已确认标记的提示/跳过。新包安装失败时，用下列原包恢复安装；已安装新包时，回滚先卸载新包：

```powershell
adb -s 3B162800PCJ00000 uninstall tv.danmaku.bili
adb -s 3B162800PCJ00000 install --no-incremental E:\software\bili-clean-patch\local\phone-backup-20260923\base.apk
```

卸载会清除登录状态、私有设置与缓存，应用专属目录中的下载也可能被删除。使用 uninstall -k 不能作为跨签名保留数据的可靠替代方案。

## 方案 C：另包名并行安装（尚未实现）

需要同时适配 provider authorities、deep links、服务绑定和登录回调；仅改 manifest 包名不能视为可靠方案。若实现，先在 AVD 验证双安装和独立数据目录，再供选择。

## 交付数据

源码和公开测试记录可以存私有 GitHub 仓库。完整 APK 仅在构建、来源与验收记录齐全后作为单独产物交付。账号数据库、cookie、签名私钥不混入源码或日志；密钥备份应使用单独加密文件与独立保管的解密凭据，不能把密钥及密码一起上传。
