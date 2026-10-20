# 改名设备识别与详细日志（0.2.1）

## 这次修正的原因

用户首次连接前已修改蓝牙显示名称。0.2.0在名称和getUuids()缓存检查处返回通用错误，尚未建立RFCOMM连接；该错误不能证明宿主Hook未命中。旧导出只包含App状态和128条简短内存事件，宿主事件留在LSPosed日志，且关闭日志会清空事件。

0.2.1不以Evo Pro显示名称或已缓存SPP UUID作为首次身份门禁。用户选中已配对设备后，先执行一次12秒、按已知产品UUID过滤的BLE身份扫描；这是身份发现，不建立GATT控制连接。每次至少间隔20秒，退出页面取消，失败不自动重试。正常RFCOMM连接仍使用公开的SDP UUID路由。

## 官方代码依据

再次校验并重组用户官方8.5.3 APK，SHA256仍为`346270d7a8d924a204f96f4cb011f9b4819df79fc609454cd651470d75571229`。实际重新读取classes5.dex的：

- `com.edifier.product_connect.connect.ble.BLEManager`：`findProductSearchUuid`从广播Service UUID匹配产品库；`getManufacturerDeviceMac`对Manufacturer ID2016取厂家数据前六字节作为经典地址；`checkAndConnectDevice`从末两字节取协议版本和encryption selector；`switchConnectMethod`按产品SPP配置选择传输。
- `com.edifier.product_connect.connect.ConnectManager.getCorrectDevice`与`getCorrectDevice$checkDevice`：规范化地址后，与目标A2DP地址进行完整相等比较。不同地址类型/地址值不能凭邻近关系归并。

独立实现的`AdvertisementIdentity`只接受已有协议Profile的四个明确产品：Evo Pro(00001e00)、W860NB Pro(00009300)、FitClip Ultra(00001303)、FitBuds Turbo(00005503)，完整UUID见源码。广播必须匹配所选经典地址、Manufacturer ID2016、version2及对应selector（XOR型号0x10，已知明文型号0）。0x31具有另一条官方协议路径，当前不支持；其他厂家布局不猜测。W820NB保留原有独立的名称+SDP旧协议路径，本次没有把它的BLE配置套入新版广播规则。

广播不是密码学身份认证。仍要求系统已配对、用户确认型号，且真实电量及ANC槽位回应通过后才开放控制。成功READY后自动保存最多32个本机地址绑定，后续改名和重连不依赖显示名称。短时广播证明最多16项、有效120秒，不能从外部Provider任意录入。

## 详细日志内容与边界

- 识别：权限、缓存UUID数量、扫描结果数量、拒绝原因计数（地址、产品、厂家、协议）、最终Profile；不记录广播原文、BLE地址或设备显示名称。
- 协议：连接代际、传输异常原因、查询命令、接收长度、帧校验拒绝计数、ANC槽位/值、解析后的电量和阶段变化。不导出任意原始通信字节。
- 宿主：实际Fragment类、PreferenceScreen类型、锚点缺失、参数key（不含值）、地址定位、跨进程连接及身份门禁等待状态。通过现有Provider的UID认证异步汇总到模块日志，同时保留LSPosed副本；每UID每10秒最多32条，队列有界。Provider不可达时仍需LSPosed片段，不能保证模块导出包含该宿主事件。
- 导出：版本、versionCode、源码提交、系统/宿主版本、服务/权限/安全开关、实际框架运行目标及已加载版本。文件写入在后台线程进行，避免原先主线程导出。

默认关闭详细采集。开启后内存最多512事件/256Ki字符，应用私有目录保留两个约256KiB的轮转文件；MAC、紧凑地址及常见凭据字段经过过滤。关闭只停止采集，已采日志仍可导出；“清空已采集诊断日志”才删除文件和内存事件。没有网络上传、原始HCI、配对材料或广播原文导出。

## 手机复现步骤

1. 安装0.2.1，启用模块。基础作用域仍为Settings和WirelessSettings；暂不启用Bluetooth电量实验。Debug证书若不同需卸载旧Debug，配置会清除，不能当作热重载测试。
2. 开启详细诊断、安全总开关，授权附近设备并启动控制服务。保持自定义显示名称即可。
3. 在系统中正常连接Evo Pro，停止官方App控制；模块中选择该已配对设备。等待12秒内的识别结果，再确认连接。
4. 若需要新授权扫描权限，授权后重新选择设备。成功后应依次出现识别Profile、CONNECTING/READING/READY。若超时，日志应有收到的匹配广播数量及拒绝统计；设备不广播或系统过滤时仍可能没有样本，这不等于型号不支持。
5. READY后打开系统耳机详情页；若无控件，在保持详细日志开启时重开所选宿主/重启后再次进入，以采集安装阶段和页面结构。查找HOST_NO_PREFERENCE_SCREEN、HOST_ANCHOR_NOT_FOUND、HOST_DEVICE_ADDRESS_NOT_FOUND、HOST_WAITING_PROTOCOL_IDENTITY等不同原因。
6. 返回模块，关闭详细采集，然后“导出脱敏诊断报告”。关闭后仍能导出此前事件；不需安装Windows分析工具，也无需重复提供官方APK。
7. 回传导出的TXT及操作时间。复现结束可用清空按钮删除本机日志。

## 验证状态

广播和日志规则的样本均是依据官方静态布局构造的合成回归，不是本用户HCI实录。新增13项测试，总数57；构建结果见STATUS。Android扫描回调、广播实际字段、Provider宿主转发、ColorOS15结构和Evo硬件控制仍需上述实机验收，不能因CI通过而标PASS。
