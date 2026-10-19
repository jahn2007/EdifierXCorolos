# 当前交付状态

版本0.1.0，优先花再Evo Pro。此文件的“源码已集成”只表示代码调用链已连接，**不表示已编译或宿主实机集成成功**。

|能力|已编译|源码已集成|已协议验证|已实机验证|
|---|---|---|---|---|
|Modern API102入口/元数据/分包|待Actions结果|是|不适用|否|
|原生Preference注入/生命周期|待Actions结果|是，结构匹配才执行|不适用|否，缺ColorOS15宿主|
|Evo F2组件电量|待Actions结果|是|公开采集样本；本项目测试待CI|否|
|Evo ANC slot1B读写|待Actions结果|是，ACK+回读|上游型号证据；本项目测试待CI|否|
|有界队列/断线/重连/切换隔离|待Actions结果|是|合成/测试transport待CI|否|
|Android单电量桥|待Actions结果|实验、默认关|接口来自较新系统参考|否|
|BLE transport|待Actions结果|实现，未绑定Evo|UUID仅参考|否|
|W820NB/W860/FitClip/FitBuds|待Actions结果|对应有限Profile|上游明确型号证据|否|
|Evo游戏/手势/EQ/其他进阶项|无|无，隐藏|无本型号命令证据|否|
|OPlus原生三电量/专属MyDevices卡片|无|无|缺ColorOS15 ABI|否|
|官方连接/开盖弹窗|无|无|缺ColorOS15接口及开盖事件|否|
|Release签名/Artifact|待Actions及Secrets|工作流已配置既有Secret名称|不适用|不适用|

已验证漫步者型号（本项目硬件实测）：**无**。上游协议证据不冒充本项目硬件测试。

## 外部限制

- 官方APK附件超过文件下载工具32MiB上限，未取得字节，未进行该APK静态分析，未枚举它的全部支持型号。
- GitHubAPI初始CONNECT403，无法读取Secrets名称（后由用户明确提供）及Actions运行；Git读成功，写权限/运行结果以实际后续命令为准。
- 无PHK110、Evo Pro、固件版本或ColorOS15宿主APK，不能完成实际控制/媒体/通话/官方弹窗验收。

当前不发布“实机最终稳定版”。CI失败必须读日志修复后再认定编译完成；网络/权限不允许读取时明确保留UNRUN，不编造run、artifact链接或APK散列。
