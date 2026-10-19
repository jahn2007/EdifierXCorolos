# 当前交付状态

版本0.1.0，优先花再Evo Pro。此文件的“源码已集成”只表示代码调用链已连接，**不表示已编译或宿主实机集成成功**。

|能力|已编译|源码已集成|已协议验证|已实机验证|
|---|---|---|---|---|
|Modern API102入口/元数据/分包|CI已通过（下方记录）|是|不适用|否|
|原生Preference注入/生命周期|CI已通过（下方记录）|是，结构匹配才执行|不适用|否，缺ColorOS15宿主|
|Evo F2组件电量|CI已通过（下方记录）|是|公开采集样本；解析测试CI通过|否|
|Evo ANC slot1B读写|CI已通过（下方记录）|是，ACK+回读|上游型号证据；编解码CI通过|否|
|有界队列/断线/重连/切换隔离|CI已通过（下方记录）|是|合成/测试transport的CI通过|否|
|Android单电量桥|CI已通过（下方记录）|实验、默认关|接口来自较新系统参考|否|
|BLE transport|CI已通过（下方记录）|实现，未绑定Evo|UUID仅参考|否|
|W820NB/W860/FitClip/FitBuds|CI已通过（下方记录）|对应有限Profile|上游明确型号证据|否|
|Evo游戏/手势/EQ/其他进阶项|无|无，隐藏|无本型号命令证据|否|
|OPlus原生三电量/专属MyDevices卡片|无|无|缺ColorOS15 ABI|否|
|官方连接/开盖弹窗|无|无|缺ColorOS15接口及开盖事件|否|
|Release签名/Artifact|Debug/unsigned已上传；正式签名失败|既有Secret映射已配置|不适用|不适用|

已验证漫步者型号（本项目硬件实测）：**无**。上游协议证据不冒充本项目硬件测试。

## 外部限制

- 官方APK附件超过文件下载工具32MiB上限，未取得字节，未进行该APK静态分析，未枚举它的全部支持型号。
- GitHubAPI初始CONNECT403，无法读取Secrets名称（后由用户明确提供）及Actions运行；Git读成功，写权限/运行结果以实际后续命令为准。
- 无PHK110、Evo Pro、固件版本或ColorOS15宿主APK，不能完成实际控制/媒体/通话/官方弹窗验收。

当前不发布“实机最终稳定版”。CI失败必须读日志修复后再认定编译完成；网络/权限不允许读取时明确保留UNRUN，不编造run、artifact链接或APK散列。

## 实际 CI 证据

- [39253318095](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39253318095)：SDK安装Action失败，未编译。
- [39253571751](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39253571751)：SDK成功，编译发现ContentProvider方法碰撞，已修复。
- [39253805018](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39253805018)：build成功，31测试、Lint、Debug/R8 Release与元数据检查通过；签名失败。
- [39254093439](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39254093439)：build再次成功；签名诊断确认既有keystore无法打开或ALIAS未匹配，尚未生成正式签名APK。正进一步区分store密码/格式与alias问题，不更换用户证书。

公开Actions页面可查看阶段结果与编译错误注解，完整日志和Artifact下载需GitHub登录/API权限。没有把Artifact ZIP的digest称为APK SHA256。最终APK的单文件SHA256由CI独立计算，放在Artifact的SHA256SUMS.txt和notice注解。
