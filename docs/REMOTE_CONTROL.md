# 可选局域网网页控制

## 架构与环境

网页服务运行在电脑、NAS 或其他常开主机上，通过已授权 ADB 控制电视。电视待机时服务器仍接收浏览器请求，米家开机单独调用 Home Assistant；不会要求电视在深睡时自己托管网页。

Python 3.10+、ADB 需要在主机可用。APK 安装还需 Android SDK Build Tools 里的 `aapt`，通过 PATH 或 `AAPT` 环境变量指定；不随项目复制 Android SDK 可执行文件。Linux / macOS 可作为主机，其他系统需自行验证。

## 初始化

先在电视上安装公开包并至少打开一次，在电视确认电脑的 ADB 授权。按 README 安装 Python 依赖，然后执行：

```sh
cd server
.venv/bin/python init.py --adb-serial '<TV_ADB_SERIAL>' \
  --url 'http://<SERVER_ADDRESS>:8768' --listen 0.0.0.0
.venv/bin/python provision.py
.venv/bin/python run.py
```

占位符需替换；`--url` 也需能被电视访问。若 mDNS 名称电视不能解析，增加 `--lan-url 'http://<SERVER_LAN_ADDRESS>:8768'`。不提供 `--listen` 时仅监听回环地址，供本机调试。

`init.py` 生成私有 `config.json`，拒绝覆盖现有配置，生成独立 8 位连接码及服务密钥。`provision.py` 只把地址和连接码写入桌面；可用 `--show-code` 在本机打印连接码。浏览器输入连接码后记住该设备 7 天。

不要直接复制 `config.example.json` 当作完整配置启动；它用于展示非敏感字段和 HA 结构，不包含认证材料。修改 `launcher_package` 时需与实际 APK 应用 ID 一致。主机地址变化后更新 URL / allowed_hosts，重新运行 provision 并重启服务。

可设置 `COUCHY_CONFIG` 指向仓库外配置文件，`COUCHY_DATA_DIR` 指向运行数据目录。`ADB` / `AAPT` 可分别指定工具的完整路径。

## 功能与边界

| 功能 | 行为 |
| --- | --- |
| 电视状态 | 读取电源交互状态、物理面板状态、前台 Activity；离线不直接判定已关机 |
| 遥控和应用 | 固定按键与启动入口；长按 OK 可打开菜单 |
| 截图 | 网页缩小预览，保存原图使用电视原生 PNG；DRM/受保护画面可能无法截取 |
| 自动刷新 | 默认关闭，启用后约 3 秒一次，后台标签页不持续截图 |
| 桌面设置 | schema 白名单内直接更新，其他项目可打开电视菜单遥控修改 |
| 系统设置 | 打开电视原生页面，结合截图和遥控操作；不是把全部系统设置复制成网页表单 |
| 文件 | 仅电视内部共享存储 `/sdcard`，普通单文件最大 2 GB；同名增加短编号，不覆盖原文件 |
| 安装 | APK 最大 1 GB；校验并保留数据更新安装，不自动卸载或降级 |
| 任务 | 状态、结果持久保存在主机；下载的服务器临时副本保留约 24 小时 |

服务不接收任意 shell 指令，不暴露电视应用私有目录，也不提供公网穿透。文件、截图和任务都属于各用户自己的运行数据，不应提交到仓库。

## 米家开关机

Home Assistant 必须已有能正常控制电视的米家集成。先在 HA 自己的界面验证实体，再把示例中的 `ha` 对象合入生成的私有配置。示例实体名是虚构占位，必须换成自己的实体。

```json
{
  "ha": {
    "base_url": "http://homeassistant.local:8123",
    "token_file": "~/.config/couchy-remote/ha-token",
    "state_entity": "media_player.living_room_tv",
    "power_on": [
      {"domain": "button", "service": "press", "entity_id": "button.living_room_tv_power_on"}
    ],
    "power_off": [
      {"domain": "media_player", "service": "turn_off", "entity_id": "media_player.living_room_tv"}
    ]
  }
}
```

在 `token_file` 自行保存 HA 访问令牌并限制文件权限，服务器只读取，不发给浏览器。`power_on` 可配置多条实际已验证动作，例如先红外再蓝牙；按顺序尝试。可选 `home_action` 与上面的动作结构一致，用于唤醒后的 HOME 恢复。`state_entity` 可省略；此时只显示 HA 通道是否可连接，不声称 HA 已提供准确电视电源状态。

关机动作必须确实让电视进入待机；某些固件的动作只是弹出电源菜单。服务器以读取到的状态确认结果，不能把“HA 接受服务请求”等同于电视已关机。媒体实体状态也可能滞后，所以优先使用电视侧电源/面板状态。

## 后台运行

使用自己的进程管理器启动 `server/run.py`，配置工作目录和虚拟环境的绝对路径；不要提交生成的系统服务文件、主机名和日志。Mac 登录项可使用 launchd，Linux 可使用 systemd，服务需随主机启动且异常退出后重启。

macOS 后台进程的局域网权限和终端运行环境可能不同，需在系统设置中为实际运行进程授予本地网络权限。完成后测试“关闭终端 → 重启服务 → 电视待机 → 重新开机”，不能只验证开发服务器。不要通过修改系统隐私数据库绕过权限。

## 访问控制

只接受回环和私有网络客户端，检查显式 Host 白名单及修改请求 Origin；连接码限速、HttpOnly / SameSite 会话、配置字段白名单和二次文件边界校验。默认 HTTP 面向可信局域网，不提供远程互联网安全承诺；不要给路由器添加端口转发。

更换配对凭据后需重新 provision；撤销已有浏览器会话可更换私有 `secret` 并重启。旧手机丢失或授权主机变更时还要在电视中撤销相应 ADB 授权。认证材料只留在本机，不能复制进 Issue 或截图。
