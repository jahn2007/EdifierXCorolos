# 实际阅读的参考来源

|项目|读取时提交|
|---|---|
|[gedo123/ColorOS-Panel-For-OriG-in](https://github.com/gedo123/ColorOS-Panel-For-OriG-in/tree/a71e50780a7ce0bbfe5cafcd35787238681714e3)|`a71e50780a7ce0bbfe5cafcd35787238681714e3`|
|[Artifical0/HuaweiPods-ColorOS](https://github.com/Artifical0/HuaweiPods-ColorOS/tree/061fad74a6cdfcf91699a3c1d069809f4578f85b)|`061fad74a6cdfcf91699a3c1d069809f4578f85b`|
|[Andrea-lyz/MelodyCodecTweaker](https://github.com/Andrea-lyz/MelodyCodecTweaker/tree/91b9e7d6d29d3703c5ba228c72a2fdde8ea82439)|`91b9e7d6d29d3703c5ba228c72a2fdde8ea82439`|
|[bqj6666/FxxkMoondrop](https://github.com/bqj6666/FxxkMoondrop/tree/bcbf6558443ce377fb626ead31906d70e2edee77)|`bcbf6558443ce377fb626ead31906d70e2edee77`|
|[silverpoetry/HyperEars](https://github.com/silverpoetry/HyperEars/tree/7acd3164ed62f63e309484e0fb75504c18d3e733)|`7acd3164ed62f63e309484e0fb75504c18d3e733`|
|[wh201906/mEDIFIER](https://github.com/wh201906/mEDIFIER/tree/10056b51ce1b354fdf2b18e35e61f8a0b8520cd5)|`10056b51ce1b354fdf2b18e35e61f8a0b8520cd5`|
|[GlitchPunkWTF/edifier-proto](https://github.com/GlitchPunkWTF/edifier-proto/tree/23fce8af4b380a968807893ae3f38ec7be5c7336)|`23fce8af4b380a968807893ae3f38ec7be5c7336`|
|[DHD2280/OPatch](https://github.com/DHD2280/OPatch/tree/869014aea6c3cac3574a1c546008e7661b741ff1)|`869014aea6c3cac3574a1c546008e7661b741ff1`|

具体阅读范围：

- OriG-in：README、NhckWlModule、MyDevicesHookInstaller、WlHookInstaller、module.prop、Gradle；借鉴API102 lifecycle/宿主ClassLoader/面板注入。ColorOS16锚点不直接硬套15。
- HuaweiPods：README、Api102HookEntry、HuaweiHfpBatteryAtHook、ColorOsSystemBatteryBridge、ColorOsAccessoryFrameworkHook、ColorOsMyDevicesHook、ColorOsWirelessSettingsHook、capture-coloros-environment.ps1；研究单电量与较新OPlus三电量接口、设备空间和弹窗，未复制华为协议。
- MelodyCodecTweaker：README、HostHookInstaller、MelodyCodecLspEntry、CodecIpc、Gradle/manifest；研究原生Preference key/Fragment定位、分层与框架service。没有移植codec native patch。
- FxxkMoondrop：README、XposedEntry、DeviceDetailsPanel、API102 hook入口与测试结构；研究设备详情内嵌、生命周期和异步状态。未移植Google Fast Pair身份伪装或Moondrop协议。
- HyperEars：README、EdifierWireCodec、EdifierEarbudAdapter、EdifierGameMode、EdifierFitBudsTurboAdapter、EdifierWireCodecTest、EdifierBatterySnapshotTest；Evo优先Profile、BES/XOR、型号槽位、F2样本与ANC白名单的主要依据。
- mEDIFIER：README、Android MainActivity.java、cmd.json；旧帧校验/SDP/具体型号范围。
- edifier-proto：README、poc.py；旧帧、W820NB命令与BLE UUID交叉验证。不执行它的爆破循环。
- OPatch：README、HookEntry.kt；确认Android15版本隔离与ColorOS15支持范围，采用独立Modern实现，不引用旧API或移植不相关功能。

libxposed/api与example的Git克隆分别返回Repository not found；main/master README raw请求404，未冒认读到这两个仓库。作为权威替代，实际下载并阅读Maven Central `io.github.libxposed:api:102.0.0:sources` 内 XposedModule、XposedModuleInterface、XposedInterface/Wrapper，以及 service102源码的 XposedService/Helper/RemotePreferences。API102无旧式入口构造参数，直接无参继承XposedModule并覆盖onPackageReady；getRemotePreferences在Hook侧只读。

权威源码地址：

- https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0-sources.jar
- https://repo.maven.apache.org/maven2/io/github/libxposed/service/102.0.0/service-102.0.0-sources.jar
- https://libxposed.github.io/api/ （运行环境尚待网络策略应用，不声称已阅读全文）

许可证见NOTICE。协议事实用独立有界Java实现；公开抓包fixture注明出处。官方APK和宿主APK仅作为研究材料，不分发其代码或产品图。

## 官方 APK 静态证据

2026-10-20 实际分析私有附件8.5.3(300)，包名com.edifier.edifierconnect。SHA256、DEX定位、产品ID290及345条内置产品研究目录见[官方APK报告](OFFICIAL_APK_ANALYSIS.md)。仅发布分析事实，不分发APK、完整配置、反编译代码或厂商图片。
