# GitHub Actions 构建与签名

唯一 APK 构建/测试渠道为 `.github/workflows/android.yml`。Windows 用户无需安装任何新增构建环境；Git 提交推送和浏览器查看 Actions 即可。Codex 环境只做源码分析、Git 与 Python 静态检查，不运行 Gradle/APK构建/单元测试。

固定：AGP 9.4.1、Gradle 9.6.0（官方 distribution SHA-256）、Temurin JDK 17、compile SDK37.0、target/minSDK35、Build Tools37.0.0、Java17、libxposed API102.0.0 compileOnly/service102.0.0、desugar2.1.5、JUnit4.13.2。Action 引用锁定已查询的实际 commit SHA；actions/setup-gradle 执行 wrapper 校验及缓存，仓库 wrapper JAR 已按官方 SHA-256核验。没有动态 Maven 版本。

触发：main push、PR、v* tag、手动 workflow_dispatch。build job 运行 `:core:test :app:lintDebug :app:assembleDebug :app:assembleRelease`，后者验证 R8 release 编译。测试结果断言至少40项且无失败、错误、跳过。上传 Debug APK、明确标注 unsigned 的 Release APK、SHA256、APK结构验证、JUnit与Lint报告。失败时仍尝试上传已有报告。

## 默认构建（暂不进行正式签名）

Push、PR、Tag与手动运行默认只执行build job，保留40项测试、Lint、Debug/R8 Release编译和模块结构校验。上传的Debug APK由Gradle使用调试证书签名，可用于安装调试；Release APK保留unsigned标识，未签名不能直接安装。默认运行不读取四项正式签名Secrets，签名问题不会再导致整轮CI失败。

以后需要正式签名时，在Android CI的Run workflow中明确勾选sign_release。现在保持关闭即可，无需修改或删除现有签名材料。

## 正式签名

用户确认现有 Secrets 名称：

|Secret|用途|
|---|---|
|KEYSTORE_BASE64|既有 keystore 的 Base64|
|KEYSTORE_PASSWORD|keystore 密码|
|ALIAS|key alias|
|KEY_PASSWORD|key 密码|

工作流直接映射以上名字，绝不生成/替换 keystore，也不索要明文。sign job 默认跳过；仅 workflow_dispatch 手动勾选 sign_release，并选择可信 main 或 v* tag 时执行，依赖 build 成功，不给 fork PR 读取 Secrets。仓库读权限即可，不自动创建 GitHub Release（最终签名 APK 存在 Actions artifact）。缺任一Secret直接失败，不回退Debug签名。

临时 keystore 在 RUNNER_TEMP，umask077；密钥密码通过 apksigner env: 参数读取，不写 Gradle 配置、不启用 shell trace。zipalign后签名，apksigner verify 校验完整性并输出公开证书指纹；manifest包名/版本、API102元数据和DEX入口结构再次检查；always步骤删除临时密钥和中间APK。签名产物包含 `signature-report.txt` 和 `SHA256SUMS.txt`，版本名/文件名为0.2.0。

构建成功和 APK 中有入口不能证明 LSPosed 实际加载、ColorOS 15 注入或真实耳机响应。CI验证 DEX entry 的 superclass 为 XposedModule，API类未打包，但运行时加载必须实机测试。

若 Secret 名称今后变化，只调整 sign step 四项映射；不要轮换用户证书作为构建修复。覆盖更新要求 applicationId 与正式证书相同，版本发布时同步递增 versionCode/versionName、文件名和校验脚本断言。

## 当前操作证据

GitHub API 初始请求在代理 CONNECT阶段返回403；环境配置草稿已添加 api.github.com，但草稿不等于运行策略已应用。Git read成功不证明API/Actions权限。实际提交、推送、Actions run与产物结果另见 STATUS，不能用工作流文件存在冒充运行成功。

首轮真实Actions运行39253318095在android-actions/setup-android步骤失败（公开页面可见步骤结论，完整日志需认证）。依据Ubuntu24 runner官方清单，改用已预装Android SDK，缺失时才由sdkmanager补充。另检查Maven AAR元数据确认API102/service102均要求minCompileSdk37，已升级编译工具链；运行目标仍是Android15/API35，不采用Android17专属运行API。

此前运行39254456996已通过32测试和全部编译/结构检查，签名停在store密码校验：EXISTING_KEYSTORE_PASSWORD_REJECTED。请在仓库 Settings → Secrets and variables → Actions 安全核对 KEYSTORE_BASE64与KEYSTORE_PASSWORD，保留原证书。ALIAS/KEY_PASSWORD尚未经过后续校验。可在Actions选Android CI → Run workflow重试；无需Windows新增任何构建环境。

## 0.2.0 热重载构建

versionCode=2；Debug、unsigned Release与正式签名产物名称均使用v0.2.0。CI现在要求40项测试，检查单一入口和autoHotReload=true。用户已修复Repository secrets，但新Actions39266976764实际仍在JKS store password完整性/密码校验阶段失败；40测试和APK编译成功，尚无正式签名产物。签名仍使用既有四项Secret，不生成密钥。

## 默认不签名构建已验证

Actions39290685717整体SUCCESS：40测试、Lint、Debug与R8 Release编译、模块/热重载元数据检查通过；正式签名job按默认配置跳过。下载当前build artifact中的Debug APK即可用于安装调试；unsigned Release只用于后续签名，不可直接安装。不需要继续处理签名Secrets才能完成普通开发构建。
