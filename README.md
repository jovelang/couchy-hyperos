# Couchy HyperOS

**基于 [conreo/couchy-launcher](https://github.com/conreo/couchy-launcher) 继续开发的非官方衍生项目。** 原桌面、基础交互和项目结构来自 Couchy Launcher；本项目增加小米电视澎湃 OS 4 适配、屏保与视频衔接改进、信号源和可选局域网控制。保留原作者版权，桌面与配套服务继续采用 **GPL-3.0-only**。

[来源与改动](UPSTREAM.md) · [开源协议](LICENSING.md) · [澎湃 OS 4 适配](docs/HYPEROS4.md) · [安装和恢复](docs/INSTALL.md) · [网页控制](docs/REMOTE_CONTROL.md) · [参与贡献](CONTRIBUTING.md)

<img src="art/couchy-launcher.svg" width="96" alt="Couchy HyperOS 几何沙发图标" />

## 有哪些功能

- 遥控器操作的单行应用、网格和分类横排；菜单上下循环，应用上键优先进入顶栏，再向左进入时间。
- 简洁线条功能图标、品牌色应用卡片；第三方 Logo 从本机已安装应用读取，不随仓库分发。
- 时钟、日期、农历、问候与可选昵称；天气需先选择城市并开启。
- 静态、视频背景与手动切换状态；视频和首帧本地缓存、单视频渲染表面、图片/视频过渡。
- HDMI 优先的信号源列表、名称前缀、自定义点按/长按动作；实时预览取决于固件和输入状态。
- 可选渐变、图片、视频屏保，分别设置进入屏保、熄屏和待机的等待时间；屏保默认关闭。
- 可选局域网网页：电视状态、前台应用、遥控、PNG 原图截图、桌面设置、系统设置入口、共享存储上传/下载、APK 安装。
- 可选 Home Assistant / 米家开关机，电视休眠时由仍在运行的服务器接收指令。

## 支持范围

| 环境 | 状态 |
| --- | --- |
| Android TV / Google TV，Android 8.0+（API 26） | 基础桌面目标环境；不同设备仍需验证 |
| 小米电视澎湃 OS 4 / Android 14 | 专项适配；详见下方实测边界 |
| 小米 MiTV-MTSU0 / interstellar，OS4.0.6.0.UTSMATV | 开发基线曾实测 HOME、中转、屏保、熄屏恢复、待机与局域网控制 |
| 其他型号、其他澎湃版本 | 未确认；请按适配文档提交能力检测和测试结果 |

公开版更换了应用 ID、移除了私人配置并将组件改为通用配置。构建检查与原开发基线的实机记录不能替代公开版在每个目标设备上的重新验证。**不承诺所有小米电视通用。** 无需 Google Play 服务；小米私有接口不可用时，基础桌面仍可使用。

## 项目结构

```text
app/                 Couchy 衍生桌面，Kotlin / Compose for TV
home-bridge/         可选 HOME 中转与 ADB 保持组件，源自 homekey-standin（MIT）
server/              可选 Python 局域网控制服务与网页
scripts/             安装检查、恢复辅助和发布前检查
docs/                架构、澎湃适配、安装、隐私和网页配置
licenses/            第三方许可文本
```

`app` 的应用 ID 为 `io.github.jovelang.couchy`；保留上游源码命名空间 `com.conreo.couchytv`，不冒充上游 APK。可用 `-PlauncherApplicationId=your.package.name` 同步改变桌面与中转目标，服务器也需匹配配置。

## 快速构建

准备 JDK 17、Android SDK Platform 34 / Build Tools 34.0.0，设置 `ANDROID_HOME` 或本机 `local.properties`。

```sh
git clone https://github.com/jovelang/couchy-hyperos.git
cd couchy-hyperos
./gradlew :app:assembleDebug :home-bridge:assembleDebug
./gradlew :app:assembleRelease :app:lintRelease :home-bridge:lintRelease
```

- 桌面调试包：`app/build/outputs/apk/debug/app-debug.apk`
- HOME 中转调试包：`home-bridge/build/outputs/apk/debug/home-bridge-debug.apk`
- 正式构建默认不签名；性能验收可使用 `./gradlew :app:assembleRelease :home-bridge:assembleRelease -PlocalSign`，以本机调试密钥签名。此选项用于本地测试。
- 正式发布可通过 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 注入自己的签名信息。两个组件使用相同签名才能共享 ADB 保持的签名权限。仓库不包含密钥。

先按 [安装文档](docs/INSTALL.md) 直接打开桌面测试，再设置 HOME。**不要先卸载系统桌面。** 普通 Android TV 通常无需中转；部分小米固件需要可选的 `home-bridge`。

## 局域网控制（可选）

电脑、NAS 或服务器需保持运行；电视休眠后，网页服务不会依赖电视本身继续运行。准备 Python 3.10+、ADB；安装 APK 还需要 Android SDK 的 `aapt`。

```sh
cd server
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
# 将两个占位符替换为你自己的授权设备 serial 和可访问的服务器地址。
.venv/bin/python init.py --adb-serial '<TV_ADB_SERIAL>' \
  --url 'http://<SERVER_ADDRESS>:8768' --listen 0.0.0.0
.venv/bin/python provision.py
.venv/bin/python run.py
```

然后用同一网络的浏览器访问配置的地址，在电视「桌面设置 → 远程控制」查看首次连接码。初始化器每次新安装生成独立凭据，不复制开发环境的地址或连接码。米家开关机需另行配置 [Home Assistant 动作](docs/REMOTE_CONTROL.md#米家开关机)。

## 默认行为与数据

昵称、天气城市、远程访问地址和连接码均为空；天气、桌面屏保、保持 ADB 与网络壁纸均不默认开启。初始背景为本地渐变。不自动获取用户定位、不包含家庭设备列表或账号。

启用天气或在线壁纸后会访问对应第三方服务；局域网控制会处理截图、文件和配置。详见 [隐私与权限](docs/PRIVACY.md)。视频/图片的所有权与软件许可证不同：仓库不包含 Apple / Amazon 壁纸媒体、第三方 APK、私人截图、运行数据或签名文件。

## 来源与许可

桌面基于 **conreo 的 Couchy Launcher**；HOME 中转基于 **fandejin/homekey-standin**；Android 14 显示控制初始化参考 **Genymobile/scrcpy**。具体版本、改动与各组件许可见 [UPSTREAM.md](UPSTREAM.md)、[LICENSING.md](LICENSING.md) 和 [NOTICE](NOTICE)。本项目与小米、Apple、Amazon 及上游作者不存在官方合作或背书关系。

## English

An unofficial GPLv3 derivative of [Couchy Launcher by conreo](https://github.com/conreo/couchy-launcher), focused on Xiaomi HyperOS 4 TV compatibility, wallpaper/screensaver transitions, input control, and an optional configurable LAN companion. Requires Android TV API 26+, JDK 17 and SDK 34 to build. New settings are primarily in Simplified Chinese; inherited translations do not imply complete translation of fork additions. See the linked documentation for provenance, installation, rollback, capability limits and privacy. No personal device configuration or proprietary wallpaper media is distributed.
