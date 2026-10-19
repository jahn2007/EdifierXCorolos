# ColorOS 15 兼容

目标：OnePlus Ace 2 PHK110，Android 15，APatch，SELinux Enforcing，API 102 LSPosed。系统构建号、无线耳机/设备空间版本待从模块报告采集。没有目标手机或宿主 APK，因此目前没有 ColorOS 15 私有接口实机 PASS。

|组件|代码行为|证据|当前限制|
|---|---|---|---|
|Settings / WirelessSettings|AndroidX Fragment 生命周期、PreferenceScreen key 与精确地址；原生 Preference 注入|FxxkMoondrop 的 AOSP 详情设计、MelodyCodecTweaker 的结构定位|ColorOS 15 实际 Fragment/方法/参数尚待确认；不会强行改未知类|
|Melody|相同受控 Preference 注入路径，只有蓝牙页面锚点匹配才生效|参考 manifest Activity DetailMainActivity/OneSpaceDetailActivity 与 EqualizerItem/pref_noise_switch|当前未依赖 R8 名称，不保证混淆后的 Preference API 可反射|
|MyDevices|相同有限生命周期探测与 Preference 插入|OriG-in MyDevicesHookInstaller（ColorOS16）|纯 ViewBinding/Compose 页面不兼容；不复制16版硬编码 panel 类|
|Bluetooth|可选 AdapterService `getAdapterService(): AdapterService` 与 `setBatteryLevel(BluetoothDevice,int,boolean):void`|HuaweiPods 的 Android 单电量桥思路|存在性/签名校验；ColorOS15待核实；开关默认关闭|
|OPlus三电量|未调用 IOplusRemoteDevice|HuaweiPods针对16.1/17实现|不能证明15 ABI一致，系统原生三电量未实现|
|Accessory|无 Hook/无作用域|HuaweiPods 的 DialogActivity/私有 protobuf 路径是较新系统|官方连接/开盖弹窗未实现；不伪装Fast Pair设备|

ColorOs15Compat 集中管理平台门禁、受控类加载、Preference 锚点和设备参数。存在类不等于语义兼容，后续版本适配必须增加实机记录和方法签名证据。无广域 Dex 扫描、ClassLoader.loadClass Hook 或所有方法遍历。

宿主不匹配时保留 ColorOS 原行为。若没有注入，按 HOST / IPC / PROFILE / TRANSPORT 分类定位；不将“找不到宿主类”报告为协议错误。所有作用域逐包隔离；不 Hook android、system_server、SystemUI、厂商控制 App。

产品图片目前使用项目自制通用耳机图标，不含官方 Evo Pro 图片；准确产品图需合法素材来源后另行适配。
