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
|Release签名/Artifact|Debug/unsigned已上传；store密码校验失败|既有Secret映射已配置|不适用|不适用|

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

## 最新验收结果（源码 1c42a0b）

[Actions 39254456996](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39254456996)：

- build **SUCCESS**：32项单元测试（0失败/跳过）、Lint、Debug APK、R8 Release APK、Manifest/DEX入口/API102元数据校验通过。
- [构建产物](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39254456996/artifacts/12186896752)：Debug APK、明确标记unsigned的Release APK及结构报告。需要GitHub登录下载。
- Debug APK单文件SHA-256：`be29ee652dbb0038fc54ad299c94b31388c770e053836f2ae8aaa7e7dcf7fba2`，来自构建步骤的实际SHA256 notice，不是artifact ZIP digest。
- sign **FAILURE**，固定诊断码：`EXISTING_KEYSTORE_PASSWORD_REJECTED`。KEYSTORE_BASE64解码成功，但keytool在打开既有keystore时拒绝KEYSTORE_PASSWORD。alias和KEY_PASSWORD尚未校验。没有生成/替换证书，没有回退debug签名。
- 用户只需在GitHub Actions Secrets中核对**既有**KEYSTORE_BASE64与KEYSTORE_PASSWORD的对应关系；不要发密钥/密码到聊天，不要新建key作为修复。修正后浏览器Actions手动运行同一工作流，或告知后继续通过推送触发。
- 本项目真实手机/耳机测试仍全部UNRUN，官方APK静态分析仍受32MiB附件传输限制。

后续仅文档/文件分卷工具提交使用skip-ci；APK代码的验证提交固定为1c42a0b，不把之后文档提交冒充已重新构建。

## 0.2.0 当前工作

API102自动/手动热重载已集成源码；旧代门禁、Preference回调/线程清理、Bluetooth worker关闭、稳定Hook替换及失败回滚均已实现。新增8个JVM回归用例，总数40，编译和正式签名待新运行验证。用户已修复签名配置，下面历史0.1.0签名错误不代表本轮结果。热重载实机仍UNRUN，初次从0.1.0升级需先重启宿主。
