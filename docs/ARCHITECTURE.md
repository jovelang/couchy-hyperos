# 结构与维护入口

## Android 桌面

源码命名空间沿用上游 `com.conreo.couchytv`，Android 应用 ID 单独定义。`AppIdentity` 从 BuildConfig 获取应用 ID，派生远程动作和虚拟设置卡片标识；变更应用 ID 时不要手工替换类名。

| 职责 | 主要文件 |
| --- | --- |
| 生命周期、HOME 和远程动作 | MainActivity.kt / Actions.kt / AppIdentity.kt |
| 配置与应用扫描 | data/Config.kt / AppRepository.kt |
| 主界面、顶栏、菜单焦点 | ui/LauncherScreen.kt / StatusBar.kt / MenuNavigation.kt |
| 品牌卡片与功能图标 | BrandArtwork.kt / BrandCard.kt / SystemAppArtwork.kt / Icons.kt |
| 视频缓存、首帧和单播放表面 | WallpaperMedia.kt / WallpaperVideoCache.kt / ManagedVideoWallpaper.kt / WallpaperPlaybackState.kt |
| 信号源与遥控键 | data/SourceController.kt / RemoteKeyRouter.kt / ui/InputSourceDialog.kt |
| 屏保与小米能力探测 | ui/ScreenSaverRuntime.kt / ScreenPower.kt / SystemIdleReceiver.kt |
| 显示恢复和系统身份桥接 | DisplayGuard.java / SaverBridge.java |
| ADB-only 配置和文件元数据 | ConfigProvider.kt / FileBridge.java |

配置用 DataStore 持久化。新字段要有默认值；网页配置更新使用 provider 的原子 patch，不能用读旧值再全量覆盖的方式抹掉电视端刚改的设置。

## HOME 中转

`home-bridge` 独立 APK 使用固件识别的 `com.ts.launcher` 身份。透明前台 Activity 等待解锁并转发；目标不存在时保留设置页回退。ADB 保持是单独的前台服务，只在权限已授予且用户启用时运行，不获取唤醒锁。

## 可选服务器

```text
浏览器 → LAN / Host / Origin 检查 → 连接码会话 → 固定 API
                                               ├─ ADB → 已授权电视
                                               └─ Home Assistant → 用户配置的米家动作
```

`configuration.py` 只处理本机配置，`core.py` 封装设备与任务，`server.py` 处理认证和 HTTP，`settings_schema.py` 限定网页可修改的桌面字段。静态网页不保存 HA 令牌；没有任意 shell 接口。

文件路径由电视端 FileBridge 再次做 canonical 共享存储边界校验。上传先写随机临时文件，校验后无覆盖提交；截图原图和网页预览分别处理；连续截图默认关闭。持久任务与下载临时文件只放本地忽略目录。

## 继续支持其他设备

把新型号的差异放到能力探测、系统设置入口和电源实现附近；不要将个人设备 IP 或 HA 实体写入源码。对非小米设备，保留标准 Android intent / HOME 路径，不能让私有 API 失败阻止基础桌面启动。新增能力需要同时更新适配矩阵、回退行为和验证记录。
