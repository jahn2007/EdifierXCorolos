# PHK110 零广播报告与官方身份备用路径（0.2.2）

## 本次用户报告的实际结论

用户报告0.2.1 code3 / f90995d，PHK110_15.0.0.830(CN01)、SDK35、LSPosed API102；Settings15.200.0、WirelessSettings15.18.5、Melody17.6.3、MyDevices15.22.20、Accessory15.7.19。两次12秒扫描均为reports=0、rejected={}。CONNECT/SCAN授权、控制服务、安全开关均开启；没有发生RFCOMM连接或协议查询，不能据此判定ANC、电量或设备协议失败。框架报告WirelessSettings等目标已加载code3，仅证明模块加载，不等于Preference页面注入成功。用户另确认系统已连接，官方App可正常控制改名后的耳机。

只公开上述适配所需的事实，不提交用户原始日志。零匹配报告不能区别设备没有广播、UUID硬件过滤未命中、Android扫描限制等原因。

## 再次读取官方代码得到的边界

实际继续读取同一SHA256官方8.5.3 APK的classes5.dex：

- `PorchConnectManager.tryReconnectTargetSpp`只为`RECORD_VERIFIED`记录恢复SPP，并要求`SPPManager.restoreProtocolForDevice`有已验证协议参数。它不能支持未经验证设备的任意盲连。
- `PorchConnectManager.tryReuseTargetSpp`要求产品标识匹配，且再次验证D8。
- `ConnectManager.receiveDataFromDevice(CommandBean)`是经过SDK的长度/CRC检查后向EDBus发布已解析命令的路径。0xD8是能力响应；`0xC9`在官方这里解析为显示名称，不能把自定义显示名称误当不可变型号。
- `SPPManager.saveProtocolForDevice`保存product UUID、bleVersion、encryption selector、manufacturer ID，不含本项目所需的配对凭据。本项目不读取或复制该私有数据库/文件。

额外核对原始DEX的field_ids/method_ids：单例字段实际名为`Companion`，并非JADX生成的别名`INSTANCE`；CommandManager/ProductManager单例getter实际为`getInstance`，SPPManager为`getGetInstance`。ConnectManager原始getter为`isReceiveD8`，并非JADX别名`getIsReceiveD8`。实现只使用实际确认的名字并检查签名。

## 可选首次身份采集

默认关闭。仅在用户选择官方App作用域并开启功能后，观察官方8.5.3(300)的`receiveDataFromDevice`，先执行原方法，仅处理0xD8；不修改返回值、不发命令、不创建/关闭官方Socket、不记录原始D8、账号、设备显示名称或配对材料。

候选需满足：官方SDK再次CRC检查通过、EC应用码、有效响应头、非空有界D8；当前传输SPP、真实Socket已连接，Socket远端地址与SDK当前A2DP设备完全相等；产品UUID属于已支持四型号，version/selector与Profile一致。获取的是官方已识别连接的事实，manufacturer ID不是此信任路径的广播门禁。

IPC接收端额外检查官方包名、UID及签名SHA256：`c897b7355f389365c7c0f21dc242626ebc39552c7f97de6a7116a16d09dc5a4c`。采用当前签名或Android认可的签名轮换历史，不信任同包名重签APK。此调用方只能提交身份候选或OFFICIAL诊断，不能读取状态、发起连接、切换ANC/游戏。可选URI读授权用于包可见性；实际每个Binder调用仍执行上述认证，query/insert等不开放。

候选绑定到完整已配对地址，最多16项，有效10分钟。用户仍需在模块选择该设备并确认，模块自行读取F2/ANC，通过后才保存READY绑定并开放写入。候选、官方连接事实和本模块实机PASS是不同状态。

## 手机操作（无需新工具）

1. [下载并安装0.2.2 build14](https://github.com/jahn2007/EdifierXCorolos/releases/download/debug-v0.2.2-build14/EdifierXCorolos-v0.2.2-debug.apk)。不同Debug证书不能覆盖；卸载重装会清除模块配置。正式签名仍默认关闭。
2. 启用模块，基础作用域Settings/WirelessSettings；本次**额外勾选`com.edifier.edifierconnect`**。Bluetooth核心作用域暂时不用开启。
3. 打开模块，开启安全总开关、详细日志和“从官方App采集首次身份”，授权附近设备并启动控制服务。
4. 在系统“应用信息”强行停止官方漫步者App，再重新打开官方**8.5.3(300)**，正常连接Evo Pro并进入控制页。这是为让D8接收回调在开启观察后重新发生，不要取消配对。
5. 回模块查看是否出现`OFFICIAL_IDENTITY_OFFERED profile=evo-pro`。然后通过系统应用信息**强行停止官方App以释放SPP**，保留系统蓝牙配对/连接。
6. 10分钟内回模块选择原来的自定义名称耳机，应使用`OFFICIAL_IDENTITY_CANDIDATE`；确认连接后观察CONNECTING/READING/READY。只有READY才是本模块协议读取成功。
7. 成功后关闭首次身份采集并取消官方App作用域；之后改名/重连使用本机已验证绑定。APK卸载会丢失绑定。
8. 不论成功与否，导出新TXT回传。报告现包含官方App版本、官方采集开关、实际框架目标和身份采集/协议阶段。若安装的官方版本/签名不同，会安全拒绝并记录，不能称已兼容所有官方版本。

## 零广播诊断增强

0.2.2处理普通和批量扫描回调，记录屏幕、电源节能、位置开关与硬件过滤支持状态。详细诊断开启且前6秒零样本时，剩余6秒进行一次有限的软件观察，整个窗口仍12秒，最多2048回调、无自动重试；身份标准保持产品/厂家/完整地址/协议字段一致。附近非目标设备只计数，目标设备仅记录厂家数据长度、协议/selector和Bluetooth基础UUID的服务编号，不记录地址、原始广播或任意UUID。

模块还记录已配对缓存是否实际含Edifier SPP，而非仅记录UUID数量。neverForLocation声明保持不变；没有请求定位权限或绕过Android过滤限制。若设备确实不广播，可选官方身份路径不依赖新广播。

## 验证状态

报告已确认0.2.1在此PHK110构建的广播身份发现失败；RFCOMM、ANC、电量、系统面板尚未执行，不标PASS。0.2.2新增10项安全策略/观察门禁回归，共67项，已由[Actions39444441994](https://github.com/jahn2007/EdifierXCorolos/actions/runs/39444441994)全部通过（0失败/错误/跳过）；Lint、Debug/R8编译、API102元数据和Debug v2签名校验通过。正式签名跳过，APK已发布并实际下载校验，散列见STATUS。真实官方App回调、跨UID传递、扫描和ColorOS页面仍待上述手机测试。
