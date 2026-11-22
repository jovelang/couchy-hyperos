# 参与贡献

本项目是 Couchy Launcher 的衍生版本。提交桌面或配套服务改动表示按 GPL-3.0-only 授权；独立 `home-bridge` 保留 MIT。不要删除上游版权，也不要把第三方媒体加进仓库。

## 开发和检查

```sh
./gradlew :app:assembleDebug :home-bridge:assembleDebug \
  :app:assembleRelease :app:lintRelease :home-bridge:lintRelease
python3 -m pip install -r server/requirements-dev.txt
ruff check server scripts
ruff format --check server scripts
python3 -m unittest discover -s server/tests -v
python3 scripts/check_public_tree.py
git diff --check
```

Android 需要 JDK 17 / SDK 34。性能和视频测试用 release；debug Compose 的开销不能代表正式版。新功能不得要求安装者拥有开发者的私有配置、签名文件或系统镜像。

新配置字段需给默认值，维护旧 JSON 的兼容；修改网页可编辑项时同步 `server/settings_schema.py`。基础 Android 功能与小米私有接口能力要区分；失败应降级，不阻止基础桌面启动。当前新增设置以中文为主，翻译贡献需保留上游已有资源并补齐新增文本。

## 设备适配

先读 [HYPEROS4.md](docs/HYPEROS4.md)，提交型号/固件、能力探测、复现步骤和实际测试结果。首次失败不要扩大尝试系统电源命令；不要停用不认识的系统包。可用设备适配 Issue 模板，真实地址、账号、SSID 和设备标识必须去除。

## 公开发布

1. 在干净克隆中构建与测试，检查工作树以及新增文件的作者和资源许可。
2. 确认 `.local/`、server/config.json、数据、日志、媒体缓存、截图、第三方 APK 和密钥均未跟踪。隐私检查只是辅助，不能替代审查。
3. 为源码提交打版本标签。若发布 APK，Release 说明应链接该标签/提交对应的完整源码和构建步骤，保留 LICENSE / NOTICE。不要把上游 APK 当作本衍生版本发布。
4. 桌面和中转若需要共享签名权限，使用同一发布密钥；密钥只通过发布环境注入，不提交到仓库。记录应用 ID、版本、构建工具与 APK 校验值，避免输出口令或家庭设备信息。
5. 说明哪些是目标设备实测，哪些只是构建检查。不要把未验证型号标为支持，也不要承诺第三方在线素材永久可用。

仓库默认 CI 只构建和提供调试产物，不自动删除/覆盖 Release，不包含私有签名配置。
