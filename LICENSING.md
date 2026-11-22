# 许可与发布规则

## 可以这样发布吗

可以按上游授予的 **GNU GPL version 3** 修改和分发衍生桌面。上游 [LICENSING.md](https://github.com/conreo/couchy-launcher/blob/d3dcd70d3dd3b261108d25d72569671aee23b2d6/LICENSING.md) 明确采用第 3 版，不擅自改为 MIT、闭源或只开放部分桌面源码。本项目桌面、配套网页服务及发布辅助代码采用 **GPL-3.0-only**，完整文本保留在 [LICENSE](LICENSE)。

本项目的落实方式：

1. 保留原版权、许可证及无担保声明，在 README、UPSTREAM、NOTICE 和应用关于页面说明衍生关系。
2. 用改动清单、日期、不同应用 ID 和版本区分衍生版本，不把问题归给上游，也不声称原桌面由本项目原创。
3. 分发 APK 时，同时提供该 APK 对应提交的完整可构建源码和安装方法；不能只给二进制，或链接到与二进制不匹配的源码。发布步骤见 CONTRIBUTING.md。
4. 保留第三方适用的版权、许可与声明。独立组件保留其原许可；GPLv3 不替代素材、品牌或服务条款。

依据：[GPLv3 第 4—6 节](https://www.gnu.org/licenses/gpl-3.0.html)、[GNU 许可证 FAQ](https://www.gnu.org/licenses/gpl-faq.html)、[Apache 关于 GPLv3 兼容性的说明](https://www.apache.org/licenses/GPL-compatibility)。这里说明本仓库采用的发布安排，不对所有未来分发方式作无条件保证。

## 第三方组件和内容

| 内容 | 处理方式 |
| --- | --- |
| Couchy Launcher | GPL-3.0-only；保留 Copyright (C) 2026 the Couchy Launcher contributors |
| homekey-standin 及中转模块 | MIT；`home-bridge/LICENSE` 和 APK 内保留 Copyright (c) 2026 fandejin；独立模块继续以 MIT 提供 |
| scrcpy 显示初始化片段 | Apache-2.0；保留 Genymobile / Romain Vimont 版权和修改说明；许可证随桌面 APK 打包 |
| AndroidX / Compose / Media3、kotlinx、OkHttp | 从构建依赖获取，保留各发行包中的许可；未把依赖作者改写为本项目作者 |
| Python 第三方库 | 由 requirements 安装；相应许可和版权见各依赖发行包，不把其改标为本项目原创 |
| 原上游 Twemoji 图形 | 本公开版已用项目自身的几何矢量图形替换，未打包；若恢复原图，应查阅 [Twemoji 图形的 CC-BY-4.0](https://github.com/twitter/twemoji/blob/master/LICENSE-GRAPHICS)，不能只引用其代码 MIT 许可 |
| Cookie / Apple 系统字体 / SF Symbols 文件 | 未打包；功能图标为代码矢量，不分发 Apple 字体或 SF Symbols 资源 |
| Apple / Amazon / 社区图片、视频 | 只保留可选在线 URL 目录，不打包原图、视频或个人缓存；选择后访问的内容不属于本仓库 GPL 授权 |
| 已安装应用的品牌 Logo | 在用户设备上从相应 APK 读取，不随源码分发；品牌归原权利人 |
| Apple 公共根证书 | 仅公开 CA 证书，不是私钥；用于指定 Apple 视频域的附加信任，来源见 NOTICE |

不包含第三方 APK、电视固件、账号数据、网络电视播放源或设备私钥。小米适配只描述和实现本项目所用接口，不分发厂商系统库。项目不代表第三方品牌或上游作者提供保证。
