# GitHub Actions 构建与签名

唯一 APK 构建/测试渠道为 `.github/workflows/android.yml`。Windows 用户无需安装任何新增构建环境；Git 提交推送和浏览器查看 Actions 即可。Codex 环境只做源码分析、Git 与 Python 静态检查，不运行 Gradle/APK构建/单元测试。

固定：AGP 8.9.1、Gradle 8.11.1（官方 distribution SHA-256）、Temurin JDK 17、compile/target SDK35、minSDK35、Build Tools35.0.0、Java17、libxposed API102.0.0 compileOnly/service102.0.0、desugar2.1.5、JUnit4.13.2。Action 引用锁定已查询的实际 commit SHA；actions/setup-gradle 执行 wrapper 校验及缓存，仓库 wrapper JAR 已按官方 SHA-256核验。没有动态 Maven 版本。

触发：main push、PR、v* tag、手动 workflow_dispatch。build job 运行 `:core:test :app:lintDebug :app:assembleDebug :app:assembleRelease`，后者验证 R8 release 编译。测试结果断言至少30项且无失败、错误、跳过。上传 Debug APK、明确标注 unsigned 的 Release APK、SHA256、APK结构验证、JUnit与Lint报告。失败时仍尝试上传已有报告。

## 正式签名

用户确认现有 Secrets 名称：

|Secret|用途|
|---|---|
|KEYSTORE_BASE64|既有 keystore 的 Base64|
|KEYSTORE_PASSWORD|keystore 密码|
|ALIAS|key alias|
|KEY_PASSWORD|key 密码|

工作流直接映射以上名字，绝不生成/替换 keystore，也不索要明文。sign job 仅可信 main 或 v* tag 的非PR运行，依赖 build 成功，不给 fork PR 读取 Secrets。仓库读权限即可，不自动创建 GitHub Release（最终签名 APK 存在 Actions artifact）。缺任一Secret直接失败，不回退Debug签名。

临时 keystore 在 RUNNER_TEMP，umask077；密钥密码通过 apksigner env: 参数读取，不写 Gradle 配置、不启用 shell trace。zipalign后签名，apksigner verify 校验完整性并输出公开证书指纹；manifest包名/版本、API102元数据和DEX入口结构再次检查；always步骤删除临时密钥和中间APK。签名产物包含 `signature-report.txt` 和 `SHA256SUMS.txt`，版本名/文件名为0.1.0。

构建成功和 APK 中有入口不能证明 LSPosed 实际加载、ColorOS 15 注入或真实耳机响应。CI验证 DEX entry 的 superclass 为 XposedModule，API类未打包，但运行时加载必须实机测试。

若 Secret 名称今后变化，只调整 sign step 四项映射；不要轮换用户证书作为构建修复。覆盖更新要求 applicationId 与正式证书相同，版本发布时同步递增 versionCode/versionName、文件名和校验脚本断言。

## 当前操作证据

GitHub API 初始请求在代理 CONNECT阶段返回403；环境配置草稿已添加 api.github.com，但草稿不等于运行策略已应用。Git read成功不证明API/Actions权限。实际提交、推送、Actions run与产物结果另见 STATUS，不能用工作流文件存在冒充运行成功。
