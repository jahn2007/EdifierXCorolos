# API 102 热重载调试（0.2.0 起）

模块声明唯一 Java 入口，`minApiVersion=102`、`targetApiVersion=102`、`autoHotReload=true`。启用 API 102 模块只是前提，运行框架必须实际实现 service102 热重载。编译/测试通过不表示 PHK110 已验证该功能。

## 使用

1. **从0.1.0升级到首次支持热重载的0.2.0时，先重启所选宿主进程或手机一次。** 0.1.0的旧入口没有接受换代回调，不能由新APK追溯补上。以后保留本功能的版本可在覆盖安装新APK后由框架自动重载。
2. 覆盖安装要求同一证书。Debug与Release证书不同，不能直接混装覆盖；当前不同Hosted Runner生成的Debug证书也未持久化，已确认build10与build11不同，不能用这两个APK验证连续覆盖热重载。卸载重装会清除配置，且不是热重载。正式签名当前按用户要求关闭，详见[构建限制](BUILD.md#debug覆盖安装限制)。
3. 框架若没有自动执行，模块内点“热重载已更新的宿主代码（API 102）”，从框架返回的运行目标中选择 STALE 或 FAILED 的进程。以API返回的opaque HookedTarget为身份，不用pid猜目标；`:cards`等子进程分别显示。
4. 模块显示真实异步结果：SUCCEEDED / FAILED / UNSUPPORTED / IN_PROGRESS / PROCESS_DIED。UP_TO_DATE不会为了配置变化强制重载。15秒没有回调显示待确认，重新查询框架状态，不自动重试或宣称成功。
5. SDK/宿主/框架版本与重载结果会进入脱敏诊断报告。新代码尝试立即恢复当前可见的原生Preference区；若ActivityThread/Fragment结构不匹配，退出再进入耳机详情页，让真实onResume重新挂载。

安全开关/日志配置继续通过remote preferences同步，不滥用热重载传播配置。初次增加作用域、首次加载、框架不支持/拒绝重载仍需重启宿主。Bluetooth电量实验继续独立默认关闭。

## 生命周期与清理

- 旧代 `onHotReloading` 先核对 Application/包名，再只把 schema、包名、代次等 Bundle primitives/String交给新代。无Fragment、View、controller、socket或旧模块Parcelable跨ClassLoader传递。
- 先关闭入口门禁，在主线程清除Preference回调、移除模块控件、取消Handler消息，停止并等待各面板worker；最多8个活跃/待清理面板，不能无限积累。
- Bluetooth桥worker单独关闭并等待，清除仍属于本模块的缓存值。同步Binder调用若未退出或主线程暂时繁忙，拒绝此次热重载，不能在旧线程仍运行时返回true。用户可关闭详情页后重试或重启相关宿主；失败后旧代入口保持关闭，防止半关闭资源再创建。
- 新代 `onHotReloaded` 自行重新解析宿主ClassLoader、安装Hook；框架不会重放onPackageReady。按稳定ID和精确Executable调用 `replaceHook` 原子替换，新增Hook正常安装，被删除的旧Hook取消。替换失败先取消旧句柄再安装，不能叠加两个拦截器。
- 新代安装失败时关闭新资源并回滚已安装句柄、清理未采用的旧Hook；向框架报告失败，保留系统默认路径。HookGeneration的替换、删除、签名变化、回滚及失败隔离由JVM回归测试覆盖。
- 已经开始的旧Hook调用遵循框架链快照；组件自己的stopping门禁阻止其在原调用返回后重建worker/UI。旧线程终止后再做一次主线程清理屏障，清掉晚到的UI任务。

没有 native patch 或 JNI线程。代码释放引用不保证Android立刻GC旧ClassLoader，需要实机观察多次换代后的内存、线程和页面情况。

## 与蓝牙控制服务的区别

热重载针对运行中的 Settings/Melody/MyDevices/Bluetooth **Hook代码**。模块App自己的前台服务不在Xposed作用域；Android覆盖安装可能结束模块进程及其RFCOMM连接。更新后若服务停止，重新打开模块、启动服务并连接Evo Pro；不会复用旧App进程内的socket或假装连接仍存在。

## 实机验收（当前全部UNRUN）

- 在0.2.0正常加载后，覆盖安装后续同证书版本；核对自动重载结果和loadedVersionCode。
- 保持原生详情页打开换代，控件只有一份，旧Preference监听器/worker数量不增长，退出页面后释放。
- 连续升级/手动重载至少10次，检查LSPosed、线程与崩溃日志，媒体/通话正常。
- 单独启用Bluetooth实验再换代，核对只有一个poller、标准电量仍优先。
- 模拟宿主销毁、Binder迟滞、接口不匹配、目标进程退出：显示真实失败，取消作用域/重启可恢复。
