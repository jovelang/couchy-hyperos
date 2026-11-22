# 来源与衍生关系

本项目不是从零编写的桌面，也不是上游官方版本。Couchy Launcher 提供了桌面骨架、Compose 布局、应用扫描、配置存储、壁纸目录及基础设置；这些成果的原作者和版权声明继续保留。

| 组件 | 原项目 | 使用依据 | 许可 |
| --- | --- | --- | --- |
| 桌面主体 | [conreo/couchy-launcher](https://github.com/conreo/couchy-launcher) | 导入基线 [d3dcd70d3dd3b261108d25d72569671aee23b2d6](https://github.com/conreo/couchy-launcher/tree/d3dcd70d3dd3b261108d25d72569671aee23b2d6) | GPL-3.0-only |
| HOME 中转 | [fandejin/homekey-standin](https://github.com/fandejin/homekey-standin) | 基线 [f41ae8b11be0957156cbdf4147b2668e03bd1808](https://github.com/fandejin/homekey-standin/tree/f41ae8b11be0957156cbdf4147b2668e03bd1808) 的前台转发方案，增加解锁重试、目标配置与可选 ADB 保持 | MIT，见 home-bridge/LICENSE |
| Android 14 显示控制 | [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) | DisplayControl 初始化与 `android_servers` 加载方法；不是打包整个 scrcpy | Apache-2.0，保留来源和版权 |

## 本分支的主要改动

2026 年 11 月开发改动包括：

- 重做顶栏、时钟、日期、农历、天气、问候、应用品牌卡片与设置组织。
- 调整 HOME / 方向键行为、菜单循环、信号源列表和实时预览、按键映射。
- 视频首帧与视频本地缓存、单渲染表面、后台恢复和切换状态，减少衔接黑闪。
- 图片、视频与动态渐变屏保；小米系统闲置事件与屏保入口协调；显示控制恢复守护。
- 可选局域网控制服务：本地认证、状态、遥控、截图、配置、文件与安装任务；Home Assistant / 米家适配。

2026-11-23 公开整理：新应用 ID、独立干净仓库、通用服务器配置、可构建的中转模块、移除私人配置及不可随代码重授权的媒体、加入来源/许可/安装/恢复/适配文档与检查。修改过的主要源码标有修改日期；版本变更见 CHANGELOG.md。

## 历史与身份

该仓库以整理后的源码快照创建新 Git 历史，避免把本地运行配置、日志或开发环境带进公开历史。新历史不表示这些上游代码由本项目原创。上游代码和作者归属以上方固定提交、原许可证及 NOTICE 为准。

应用 ID 用于区分安装，保留 `com.conreo.couchytv` 源码命名空间用于保持来源可追溯。上游商标、第三方品牌和媒体内容不因代码采用 GPLv3 而被重新授权。
