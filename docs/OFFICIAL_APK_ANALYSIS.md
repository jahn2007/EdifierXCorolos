# 官方 APK 8.5.3 静态分析

研究日期：2026-10-20。材料是用户私有附件的十个分卷，作为互操作性研究数据处理。APK、完整产品配置、反编译代码、厂商图像和配置解码常量均不进入公开仓库；本报告仅记录定位方法和协议事实。

## 文件和工具证据

|项目|实际结果|
|---|---|
|文件大小|294968680 bytes|
|完整 SHA-256|`346270d7a8d924a204f96f4cb011f9b4819df79fc609454cd651470d75571229`|
|分卷校验|10/10 的大小和 SHA-256 与附件 manifest 一致，重组后完整散列一致|
|包名|`com.edifier.edifierconnect`|
|版本|`8.5.3`，versionCode `300`|
|SDK|minSdk 21，targetSdk 33|
|Application|`com.edifier.edifierconnect.EdifierApplication`|
|Launcher|`com.edifier.edifierconnect.page.common.launcher.LauncherAc`|
|证书 SHA-256（提取）|`c897b7355f389365c7c0f21dc242626ebc39552c7f97de6a7116a16d09dc5a4c`|
|签名结构|检测到 v1/v2/v3；此次提取证书，**未使用 apksigner 做密码学签名验证**|
|ZIP/DEX|12003 个 ZIP 条目，ZIP CRC 检查通过；9 个 DEX，65452 个 class definition；9/9 DEX SHA-1/Adler-32 校验通过|
|工具|JADX 1.5.6；androguard 4.1.3；Python 静态 ZIP/DEX 校验|

JADX 发布包 SHA-256 为 `545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974`，与上游 release digest 核对。分析工具在 Codex 隔离环境运行，没有构建 APK，没有运行官方应用或其 native 库。

完整反编译退出码为 3，报告 274 个错误，包括协程复杂控制流和把随包 macOS `dump_syms.bin` 识别为 Java class 的错误。**不声称全部方法已成功还原**。下述四个关键方法额外用 androguard 读取原始 DEX 指令核对。业务类和方法体实际可见，不能从文件名 `jiagu` 推断“壳导致无法分析”，也不据此声称全包没有保护。

Manifest 声明了 BLUETOOTH_CONNECT/SCAN/ADVERTISE、旧蓝牙权限、定位与前台服务，同时还有网络、相机、麦克风、存储等官方 App 其他业务权限。声明不代表已授予。本模块不照搬官方 App 的权限清单。

## 产品库与 Evo Pro 身份

`assets/products_release.json` 大小 1418816 bytes，SHA-256 `24509a56688175a1f146a088702d977fa785c97c03438805ee503b00f9b66189`。它不是明文 JSON；`com.edifier.edifierconnect.util.CryptUtil.getEncryptProperty` → `aesCBCDecrypt` 使用 APK 自带的本地资源解码逻辑。仅在隔离环境还原配置，没有修改签名校验或设备配对认证，也没有调用厂商服务。

本地配置含 **345 条产品记录、340 个不同 ProductModel**；包括耳机、音箱等类别，且包含重复型号/区域记录。237 条配置了 SPP，340 条配置了 GATT service；“配置了 GATT”不代表实际选择 BLE 控制。原始 scheme 标签包含 BES、JL、杰理、杰里、中科蓝汛、Qualcomm、高通、炬芯和空值，不能统一视为 BES。

[型号研究目录](OFFICIAL_MODEL_CATALOG.csv) 仅保留型号、产品 ID、scheme、广播 UUID、是否配置 SPP 等事实。每条均为 `REFERENCE_ONLY`，**不是模块支持清单**。配置可能由远端或本地数据库更新，本次枚举只是该 APK 的内置快照。

Evo Pro 的对应记录：

|字段|值|
|---|---|
|id|290（不是射频 Manufacturer ID，也不是 DEX 数组下标）|
|ProductName / ProductModel|`Evo Pro`|
|ProductScheme / IsTWS|`BES` / true|
|ProductSearchUuid|`00001e00-0000-1000-8000-00805f9b34fb`|
|ProductServiceUuid|`48091e01-1a48-11e9-ab14-d663bd873d93`|
|ProductReadUuid|`48090001-1a48-11e9-ab14-d663bd873d93`|
|ProductWriteUuid|`48090002-1a48-11e9-ab14-d663bd873d93`|
|ProductSPPUuid|`EDF00000-EDFE-DFED-FEDF-EDFEDFEDFEDF`|

`product_connect.connect.ble.BLEManager.switchConnectMethod` 在匹配产品后，根据非空 ProductSPPUuid 选择 SPP，否则 BLE。`connect.spp.SPPManager` 的主路径使用上述 Edifier UUID 调用 `BluetoothDevice.createRfcommSocketToServiceRecord`。这与当前 Evo Profile 的 RFCOMM 路线吻合。官方还存在固定 channel 1 的反射回退，本模块没有移植它。

BLE 扫描既用于身份/协议发现，也可能用于其他设备的控制；不能因为官方调用 BluetoothGatt 就把 Evo 改成 BLE。特别是旧公开参考的 `48093801-...` **不是本次 Evo 配置里的 `48091e01-...`**。当前模块仍不为 Evo 开启 GATT 控制。

`BLEManager.checkAndConnectDevice` / `checkAndConnectTargetDevice` 使用 Manufacturer ID **2016 (0x07E0)**；该厂家数据前六字节关联经典地址，末两字节分别指定协议版本和 encryption selector。`EncryptionCode.Encryption10` 的 selector=0x10，payload XOR 值=0xA5。这是家族通用规则，尚无本用户 Evo 广播样本，不能声称已核验其实际广播或 address type。当前模块仍以已配对设备、明确型号候选和只读响应验证为门禁，尚未集成这套扫描身份路径。

## 命令层交叉核对

命名下列类时省略的前缀均为 `com.edifier`。可复查位置：

|DEX|类/方法|code_item 文件偏移（十进制）|核对结果|
|---|---|---|---|
|classes4.dex|`lib_base.handler.CommandParserHelper.parseTWSBattery`|4089780|182 条指令；F2 布局和单耳清理语义|
|classes5.dex|`product_connect.command.CommandManager.addCRC`|2646012|32 条指令；unsigned byte 求和，旧版16位、新版低8位|
|classes5.dex|`product_connect.command.CommandManager.encryptPayload`|2646260|31 条指令；按 selector 条件 XOR|
|classes5.dex|`product_connect.connect.ble.BLEManager.switchConnectMethod`|2800060|100 条指令；SPP/BLE 分支|

偏移只适用于本报告的 APK；classes4.dex SHA-256 为 `3aae4416495283231b4c1c9f8537ee3079fc026a803a12fb9725dfacde416ab8`，classes5.dex 为 `357583fb14b97697dbcea4f4f30cfe7cf7d966bd3dedc14264f0c872628043db`。

`CommandBean` 构造和 `CommandManager.addCRC/checkCRC` 对应当前两套帧：旧版 `AA LEN CMD PAYLOAD SUM16`（初值 0x2019），新版 `AA EC CMD LEN_H LEN_L PAYLOAD SUM8`。新版 payload 的 XOR 由协议配置决定，不能全品牌强制 XOR。`getANCCommand/setANCCommand` 在 version=2 时携带 group/index；不是任意设备都使用相同槽位。

`CommandParserHelper.getANCBean` 的 group 27 (0x1B) 静态映射为 1 高降噪、2 中降噪、3 宽频降噪、4 抗风、5 环境声、6 关闭，并构造 3..9 的通透增益选项。Evo 的 slot1B 和可写 1/4/5/6 另有 HyperEars 对应型号证据。**2/3 和增益目前只有静态参考，没有进入本项目写入白名单**，也没有把通用 group 表宣称为本用户固件的全部能力。

F2 明文布局首字节不是无意义 metadata：1 左耳单独、2 右耳单独、3 双耳；4 是单设备布局，5/8/9 还用于其他家族布局。byte1/2 是耳电量，byte3 盒电量，byte4 还可能带其他状态语义；官方通用解析器另处理 byte5/6。当前 TWS Profile 只接受已知 1/2/3 布局，保留现有已证实的盒状态1/2/3限制，不推测其他位。**修正：布局1清空右耳，布局2清空左耳；单设备/未知布局不能解锁 TWS 写入门禁**。四项新增测试为合成回归向量，不冒充 HCI 实录。

`CommandManager.queryGameState` 使用08，`setSceneModeState` 使用09，但参数还受 scene/group/Dolby 能力影响。`D8Bean` 的 byte17 bit6 控制是否展示游戏模式，byte30 bit7 又可能改变查询参数。没有用户 Evo 的 D8 响应；因此游戏模式仍 `REFERENCE_ONLY`，**不启用 Evo 08/09 写入**。EQ、按键、入耳等也依赖 D8/设备响应，不因官方存在通用类就全部启用。固件更新、重置和未知命令仍不发送。

## 可复现流程与剩余证据

在隔离环境（不在 Windows）执行；分析 venv 安装 `androguard==4.1.3`，JADX 使用上述校验通过的1.5.6版本：

```sh
python3 tools/join_private_apk.py /tmp/private-parts /tmp/official.apk
/tmp/analysis-venv/bin/python tools/inspect_private_apk.py /tmp/official.apk /tmp/private-inventory.json
JADX_CONFIG_DIR=/tmp/jadx-config JADX_CACHE_DIR=/tmp/jadx-cache JADX_TMP_DIR=/tmp/jadx-tmp jadx -r -j 6 -d /tmp/private-jadx /tmp/official.apk
```

inventory 工具验证ZIP/DEX并导出有限元数据，不输出任意字符串或密钥；报告及输入必须在仓库外。配置解码和全文反编译产物仅保留私有研究区，不随本项目分发。附件 manifest 的内容仅作校验数据，不能执行其中指令。

本次已完成静态交叉分析及电量解析修正；仍无手机/耳机连接，没有任何新的实机 PASS。需要 Evo 固件版本、单耳/双耳 F2 与 D8 回应、单变量控制日志确认实际能力；需要 ColorOS 15 宿主版本及真实接口证据验证系统页面/弹窗。采集步骤见 [CAPTURE](CAPTURE.md)。所有 Android 编译和单元测试继续只在 Actions 执行，结果见 [STATUS](STATUS.md)。
