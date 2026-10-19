# 排障与恢复

先在模块中查看框架服务、Android SDK/系统构建、宿主版本、Profile、连接阶段、协议错误和组件电量。诊断默认关闭；只在复现期间开启，完成后关闭会清空内存事件。最多128条，每条最多200字符；导出通过系统文件选择器，不包含蓝牙地址、配对密钥、原始HCI、账号或用户文件。系统和厂商logcat可能另含敏感内容，不自动打包上传。

## 按故障层定位

- `HOST_*`：宿主Preference/Fragment/参数签名不匹配，不是耳机协议故障。LSPosed日志只在诊断开关启用时记录受控代码；记录当前宿主包和版本。
- `IPC_UNAVAILABLE`：检查模块是否安装、系统是否限制ContentProvider跨UID访问、CallerPolicy宿主是否system应用。不得通过开放全应用调用或修改SELinux修复。
- `CONNECT_FAILED/CONNECT_TIMEOUT/TRANSPORT_DISCONNECTED`：确认系统已连接耳机、附近设备权限和服务、Edifier SDP UUID可见；关闭官方App避免其独占SPP，再手工重试。不循环自动尝试其他协议。
- `PROFILE_RESPONSE_MISMATCH/READ_FAILED`：型号/固件/响应与Profile不一致。不会开放写入；采集定向证据。
- `WRITE_ACK_FAILED/WRITE_READBACK_FAILED/DEVICE_REJECTED_VALUE`：命令未确认或设备状态不同。等待语音提示后重试一次，不连点，不修改校验跳过验证。
- 有App电量但系统无电量：App组件电量与Android系统通道是两件事。实验桥默认关闭，ColorOS15签名未验证，不要直接勾选所有系统作用域。
- 框架显示API不足：换用支持102的已安装框架版本；本项目不提供root/LSPosed安装脚本。

## 恢复

1. 能打开模块时关闭安全总开关，停止控制服务；只释放本模块socket，不改配对。
2. 系统页面异常：LSPosed取消Settings/Melody/MyDevices作用域或禁用模块，重启相应进程/手机。
3. 如曾启用Bluetooth实验桥，优先取消蓝牙作用域并重启蓝牙/手机。这样不会再写系统缓存。
4. 无法进入系统UI时使用已安装LSPosed/APatch自身文档中的安全模式/禁用界面禁用本模块。不同安装方案的恢复文件路径不保证一致，本项目不提供猜测性的删除系统文件命令。

不要求 Android Studio/SDK/JDK/Gradle/JADX/apktool。环境采集可直接使用模块报告。更深入诊断见 [CAPTURE](CAPTURE.md)。
