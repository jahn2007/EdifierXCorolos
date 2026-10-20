# 漫步者协议分析

## 研究范围与等级

优先花再 Evo Pro。`CONFIRMED` 指该命令有对应型号公开实机证据，**不是本项目已做实机验证**；`REFERENCE_ONLY` 为其他型号/未完成本型号验证；`HYPOTHESIS` 不进入发送路径；`UNSUPPORTED` 不实现。还应分别查看编译、源码集成、协议样本、实机四种状态。

已于 2026-10-20 收齐十个分卷，完成逐卷及整体校验，并实际静态读取官方 `8.5.3 (300)` APK。完整 SHA-256 为 `346270d7a8d924a204f96f4cb011f9b4819df79fc609454cd651470d75571229`，包名 `com.edifier.edifierconnect`。证据、方法定位和反编译局限见 [官方 APK 分析](OFFICIAL_APK_ANALYSIS.md)，内置345条产品研究目录见 [CSV](OFFICIAL_MODEL_CATALOG.csv)。目录不等于全部已适配。APK/完整配置/反编译源码/厂商素材不提交公开仓库。

## Evo Pro 的已知通道与帧

RFCOMM SDP UUID：`EDF00000-EDFE-DFED-FEDF-EDFEDFEDFEDF`，使用 public secure createRfcommSocketToServiceRecord。未验证 Evo Pro 的 BLE 服务映射，不同时盲连两种通道。

请求 `AA EC CMD LEN_H LEN_L PAYLOAD... SUM8`；返回 `BB` 或 `CC` 开头，APP_CODE 必须 EC；长度是 payload 大小，大端。校验为全部前置字节之和 `& 0xff`，不是 CRC 多项式算法。Evo Pro 数据 payload XOR `0xA5`；无参数查询不加 XOR。最大接收 payload 250 字节、缓存 8192 字节，非法头、长度、校验和请求回声均拒绝。

|功能|型号|命令/参数|回应解码与校验|握手/订阅|证据和等级|未确认问题|
|---|---|---|---|---|---|---|
|组件电量读取|花再 Evo Pro|F2，无参数|XOR 后至少 5 字节；byte0 布局1左单耳/2右单耳/3双耳（缺席耳清空），byte1/2 左右耳，byte3 盒，byte4 盒状态；1 charging/2 not charging/3 offline；耳 0 表未知；范围 0..100|无额外配对绕过；作为只读就绪探测，保持 RFCOMM 读循环|HyperEars EdifierWireCodec 与测试，`CONFIRMED`（上游采集）；官方 parseTWSBattery 静态交叉核对|本设备固件和通知频率待实测；盒 offline 时不显示百分比|
|降噪读取|花再 Evo Pro|CC，无参数|XOR 后 `[1B,value]`；1/2/3 ANC（官方group27静态名称见报告）/4 抗风/5 通透/6 关闭|READY 要求此槽位匹配；无额外订阅命令|EdifierAncDialects.EVO_PRO，`CONFIRMED`（上游）|各固件主动通知/延迟需实测|
|降噪写入|花再 Evo Pro|C1；明文 `[1B,01/04/05/06]` 后 XOR|等待 C1 ACK，再冷却 2 秒并 CC 回读；不能只收到 socket write 就报成功|必须电量及 ANC 握手通过|EdifierEvoProAdapter 与 writeValues，`CONFIRMED`（上游）|2/3 只有读取映射，未进入正式写入选项；本机 ACK/回读时序待实测|
|游戏模式|花再 Evo Pro|08/09 不能因其他型号存在而直接使用|无本型号启用路径|不探测|`REFERENCE_ONLY`|官方 D8 byte17 bit6 控制游戏能力，实际 Evo D8 未取得；上游未启用，需定向采集|
|版本/设备名/功能表|花再 Evo Pro|C6/C9/D8 存在家族参考|当前不发送，避免未确认响应格式参与身份判定|不适用|`REFERENCE_ONLY`|型号/固件响应字段未确认|
|手势/EQ/入耳/双连/查找/空间音效|花再 Evo Pro|无已验证本型号白名单|无写入|不适用|`UNSUPPORTED`|需官方 App 定向单变量采集|
|固件升级/重置/关机/重配|全部|禁止发送 07/CD/CE/CF/D1 等危险命令|不提供任意命令入口|不适用|`UNSUPPORTED`|默认不研究/实现 OTA 写入|

来源样本（公开、无地址）：

```text
AA EC F2 00 00 88                   查询组件电量
BB EC F2 00 06 A6 C1 C7 A5 A6 B4 CC  回应解码 03 64 62 00 03 11：左100，右98，盒offline
BB EC CC 00 02 BE A3 D6              降噪回应解码 1B 06：关闭
```

官方 group27 静态映射为高/中/宽频降噪，但本用户固件未验证；当前 Noise value 1/2/3 仍按上游仅显示“主动降噪”，正式写入只使用上游明确列出的 1/4/5/6。测试中的 ACK 变体为按公开帧规则生成的合成向量，与公开实录样本分开标注。

## 其他明确型号与差异

|型号|帧/通道|电量|降噪|游戏|进入代码的依据与边界|
|---|---|---|---|---|---|
|W860NB PRO|BES XOR，RFCOMM Edifier UUID|D0 单电量|slot10；1/3/4/5 写入；2只读|禁用|HyperEars 上游实测；`CONFIRMED` 上游，未做本项目实机|
|W820NB|legacy，RFCOMM 相同 UUID|D0 单电量|C1 01关闭/02降噪/03通透，CC读取|08查询、09单字节0/1|mEDIFIER 与 edifier-proto W820NB，`CONFIRMED` 上游；不自动涵盖 Plus/Double Gold|
|FitClip Ultra|BES XOR，RFCOMM|F2 三组件|明确禁止 CC 探测|08/09 XOR|HyperEars 上游实机；ANC 查询曾导致断开|
|FitBuds Turbo|BES **明文**，RFCOMM|F2 三组件|slot1B；与 Evo 相同槽位但不 XOR|08/09明文|HyperEars PR62 代码/样本，`CONFIRMED` 上游；不得套用 Evo XOR|
|W200BT Plus、W820NB Double Gold|legacy 参考|型号级能力差异未完成审计|不借用其他型号|禁用|mEDIFIER README 实测声明，当前 `REFERENCE_ONLY`，不匹配、不写入|
|官方 App 其他全部型号|未知/各不相同|未知|未知|未知|`UNSUPPORTED`，不能以品牌名自动套用|

legacy 帧：`AA LEN CMD PAYLOAD SUM_H SUM_L`，LEN=payload+1；校验 `0x2019 + sum(header..payload)` 大端16位。BLE 参考服务 `48093801-1a48-11e9-ab14-d663bd873d93`、写 `48090002-...`、通知 `48090001-...` 来自 edifier-proto poc.py，只提供受控 transport 实现，尚未对 Evo Pro 启用。

## 后续证据采集

见 [CAPTURE](CAPTURE.md)。APK 已完成静态分析，仍需 Evo Pro 当前固件、官方 App 单变量日志、ColorOS 15 宿主版本/可合法提供的组件 APK。不会运行参考 poc.py 的扫描/爆破循环。配对认证、firmware 安全机制和 SELinux 保持不变。
