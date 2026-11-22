# 公开整理验证记录

日期：2026-11-23。以下结果针对本次公开源码快照，不代表所有电视型号均已实测。

- 从只含 Git 跟踪文件的干净导出目录构建成功：桌面 debug、HOME 中转 debug、桌面 R8 release，以及两个模块的 release lint。
- 桌面 lint：0 errors / 127 warnings；HOME 中转 lint：0 errors / 3 warnings。保留警告，没有用总开关关闭 lint。
- 两个调试 APK 均通过签名验证，签名一致，应用 ID 分别为 `io.github.jovelang.couchy` 与 `com.ts.launcher`。
- 网页服务 12 项隔离测试通过：配置初始化、凭据唯一性、Host/Origin/会话、未授权安装拒绝、配置白名单、配对码隐藏、共享存储边界、参数转义、原图/预览、未配置电源动作拒绝。
- Python Ruff 检查与格式检查通过；Git whitespace 检查通过。
- 跟踪树已做隐私和资源审查；未包含设备地址、昵称配置、位置、账号令牌、连接码、私钥、运行缓存或第三方壁纸媒体。

公开应用 ID 的实机安装验收尚未重新执行。已知硬件验证来自公开整理前的开发基线，适配范围和复验项目见 [HYPEROS4.md](HYPEROS4.md)。本次没有向原电视安装新包或改动现有设置。

依赖当前仍有非致命的 Starlette/httpx 测试客户端弃用提示；隔离测试结果通过。持续集成会在每次提交重新构建和测试，具体运行结果以 GitHub Actions 为准。
