# EdifierXCorolos

**[直接下载 v0.2.0 Debug APK](https://github.com/jahn2007/EdifierXCorolos/releases/download/debug-v0.2.0-build11/EdifierXCorolos-v0.2.0-debug.apk)** · [所有开发构建](https://github.com/jahn2007/EdifierXCorolos/releases)

为 OnePlus Ace 2 / PHK110、ColorOS 15 / Android 15 开发的 Modern libxposed API 102 模块。**优先适配花再 Evo Pro**。目标是系统蓝牙详情页内的真实耳机控制，不以普通 App 页面代替原生控制。

当前为 **0.2.0 开发版本**。源码中的系统集成和协议实现不等于 PHK110 实测成功；构建结果、协议证据、宿主兼容与实机验收分别记录在 [STATUS](docs/STATUS.md)。尚无本项目实机验证型号，勿称最终稳定版。

官方8.5.3 APK现已通过十个分卷完整校验并完成静态分析，定位Evo Pro产品ID290及SPP配置，详见[分析报告](docs/OFFICIAL_APK_ANALYSIS.md)。内置345条产品记录仅作研究目录，不等于全部适配。

## 已实现代码

- 安全的分包 Hook，宿主 ClassLoader、Android 15/品牌门禁、Fragment 生命周期与原生 Preference 插入。找不到地址、Preference 锚点或签名时保留原页面。
- Evo Pro 独立 Profile：BES/RFCOMM，`0x1B` ANC 槽位，主动降噪、抗风噪、通透、关闭；`0xF2` 左/右/盒电量原子快照。仅收到有效电量与本型号 ANC 响应后允许写入。
- 每次写入等待 ACK、冷却后回读；显示设备实际状态。串行有界队列、命令/连接超时、代际隔离、断线清理、ACL 重连和 30 秒只读刷新。
- 默认关闭的 Android 原生单电量实验桥接。标准电量优先，绝不在只有一个百分比的系统通道伪装三项电量。
- 明暗主题跟随宿主；模块提供服务控制、身份确认、改名后记忆和脱敏诊断导出。
- Actions 中执行单元测试、Lint、Debug/R8 Release 构建、签名及 APK 结构检查，上传报告和 SHA-256。

Evo Pro 游戏模式、EQ、手势、入耳检测、官方连接/开盖弹窗、MyDevices 专属卡片与 OPlus TWS 原生三电量接口仍需要对应型号/ColorOS 15 证据；**不会显示假开关**。兼容候选 Profile 见 [协议报告](docs/PROTOCOL_ANALYSIS.md)，不承诺官方 App 全型号已适配。

最新 [CI运行](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39426768487) 已通过44项测试、Lint和APK编译/结构检查；当前默认关闭正式签名，提供可安装调试的Debug APK和未签名Release APK；以后可手动勾选sign_release启用既有证书签名。热重载尚待实机验收。

支持 API 102 自动/手动热重载，更新APK后可更换已注入宿主的Hook代码；会清理旧代控件、监听器与线程。**从0.1.0首次升级后仍须重启宿主一次**，后续同证书版本才可连续换代；当前临时Debug构建的证书限制见安装步骤。操作、边界和失败恢复见 [HOT_RELOAD](docs/HOT_RELOAD.md)。

## 安装和启用

1. 当前优先从上方 Release 页面下载 Assets 中的 `EdifierXCorolos-v0.2.0-debug.apk` 并安装，核对同页 `DEBUG-SHA256SUMS.txt`。也可登录 GitHub 后从成功 Actions 的独立 `EdifierXCorolos-v0.2.0-debug-*` artifact 下载 ZIP 并解压得到 APK；同包的 unsigned Release APK不能直接安装。以后启用正式签名时，从 `EdifierXCorolos-v0.2.0-signed-*` 获取正式版并核对 `signature-report.txt`。Debug与正式版证书不同，不能直接互相覆盖。本次build11与之前build10的Debug证书也不同，不能覆盖安装或用它验证跨构建热重载；如需安装新版，卸载旧Debug会清除模块配置，先保留诊断信息，按下文重新授权和连接。
2. 在支持 API 102 的 LSPosed 中启用模块。基础作用域：`com.android.settings`、`com.oplus.wirelesssettings`。需要验证 Melody/MyDevices 详情页时再勾选 `com.oplus.melody`、`com.heytap.mydevices`。
3. `com.android.bluetooth` 仅用于可选系统电量实验；默认不勾选，也不要选择系统框架、SystemUI 或 Accessory。APK 建议作用域包含蓝牙包，但代码开关默认关闭，需手工启用才安装该 Hook。
4. 重启所选宿主进程或手机。打开模块，打开安全总开关、授权“附近设备”，启动前台控制服务。
5. 在系统中正常配对并连接 Evo Pro。模块“选择已配对耳机并连接”，核对型号。要求原始型号名和已缓存的 Edifier SDP UUID 同时匹配；不主动持续扫描。
6. 等待状态 `READY`，可点击“记住已验证设备”保留地址绑定，随后改蓝牙显示名称仍可识别。打开 **设置 → 蓝牙 → 对应耳机详情**，检查是否出现原生控制区。
7. 若宿主没有命中，开启短时诊断并按 [排障文档](docs/TROUBLESHOOTING.md) 采集版本信息，不能视为耳机协议故障。

系统电量实验默认关闭；要测试需在模块启用独立开关并勾选蓝牙作用域、重启该进程。没有 ColorOS 15 方法签名证据前不应当成已支持功能。

## 构建与恢复

所有 APK 构建、测试、签名仅在 GitHub Actions 进行；Windows 只需要 Git 和浏览器，不要求 Android Studio、SDK、JDK、Gradle 或逆向工具。详见 [BUILD](docs/BUILD.md)。

发生异常：先关闭模块安全开关和控制服务；若系统页面异常，在 LSPosed 取消本模块作用域或禁用模块并重启。若已单独启用蓝牙电量实验，先取消蓝牙作用域。无需改系统 APK、系统分区或 SELinux。

[架构](docs/ARCHITECTURE.md) · [协议](docs/PROTOCOL_ANALYSIS.md) · [ColorOS 15](docs/COLOROS15_COMPAT.md) · [测试](docs/TESTING.md) · [来源](docs/REFERENCES.md)
