# 安装、设置 HOME 与恢复

先阅读 [支持边界](HYPEROS4.md)。以下命令的设备 serial 必须来自你自己电视上已经确认授权的 ADB 连接；不要使用别人的地址或配置。

## 1. 直接打开桌面

```sh
export TV_SERIAL='<TV_IP>:5555'
adb connect "$TV_SERIAL"
adb devices
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TV_SERIAL" shell am start -n io.github.jovelang.couchy/com.conreo.couchytv.MainActivity
```

在设置里检查布局、遥控、应用启动、退出恢复、背景和信号源。此时无需禁用系统桌面，也无需授予高级权限。

公开应用 ID 与原版 Couchy 不同，可并存。旧的私人构建、其他签名的中转组件不会被本项目自动覆盖；出现签名冲突时先确认旧包用途和恢复方案，不要盲目卸载。

## 2. 普通 Android TV

按 HOME 后如果出现选择框，选择 Couchy HyperOS 并设为始终。也可以执行：

```sh
adb -s "$TV_SERIAL" shell cmd package set-home-activity io.github.jovelang.couchy/com.conreo.couchytv.MainActivity
adb -s "$TV_SERIAL" shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```

如果 HOME 仍被固件强制接管，先做下面的小米中转验证。不能只依据 `set-home-activity` 返回 Success 判断适配成功。

## 3. 小米澎湃 OS 4 的可选 HOME 中转

某些已知固件识别 `com.ts.launcher` 作为第三方桌面入口。`home-bridge` 使用这一兼容身份，并不属于小米或该包名原持有者的官方应用；同名组件只能安装一个。先确认没有需要保留的同名包。

为了签名权限关系，推荐用同一次构建、同一密钥的两个 APK，先装中转再装桌面：

```sh
adb -s "$TV_SERIAL" install -r home-bridge/build/outputs/apk/debug/home-bridge-debug.apk
adb -s "$TV_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
python3 scripts/tv_setup.py --serial "$TV_SERIAL" doctor
```

确认新桌面可直接启动、原 HOME 仍然可用、电脑仍能连接 ADB 后，选择一种方式。首次执行会在本机被忽略的 `.local/` 保存原 HOME 和原系统包状态（目录/文件权限 700/600）；不要公开这个设备记录。后续操作不覆盖原 HOME。

### 普通默认项

```sh
python3 scripts/tv_setup.py --serial "$TV_SERIAL" activate --bridge
```

适用于只需中转、不支持持久能力的固件。系统桌面更新后普通默认项仍可能失效。旧版 `--disable-stock` 已移除：不再通过停用系统桌面解决默认项问题。

### 电视端持久默认项

先核对 [适配条件](HYPEROS4.md#电视端持久-home可选)。当前仅开放 Android 14 用户 0，脚本会检查可选库及管理端点；这不替代实际 system UID 和固件行为验证。

```sh
python3 scripts/tv_setup.py --serial "$TV_SERIAL" activate --bridge --persistent
adb -s "$TV_SERIAL" shell content call --uri content://com.ts.launcher.homepolicy --method status
adb -s "$TV_SERIAL" shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "$TV_SERIAL" logcat -d -s CouchyHomePolicy:I '*:S'
```

确认：`enabled=true`，实际解析为 `com.ts.launcher/com.homekey.standin.MainActivity`，本次日志有 `Installed persistent HOME=...; uid=1000`。`lastCommandAccepted=true` **只代表请求被接收**，不能独立证明帮助程序执行成功。脚本不会通过同时设置普通默认项掩盖新持久请求失败；已有普通默认项仍需通过回归验证排除。

系统桌面保留启用。如果请求失败，按恢复章节关闭策略；不要改为卸载系统包。启用后退出本机守护和其他自动发 HOME 的程序，断开 ADB，再测试实体 HOME、待机唤醒、完整重启。系统桌面安装更新回归需使用自己的、来源可信且签名匹配的 APK；仓库不提供厂商 APK。

如果已经在用中转但没有原 HOME 记录，脚本会拒绝伪造恢复基线。先按下面的手动方法恢复并记录实际系统 HOME，再启用。自定义应用 ID 的构建需在脚本参数中同时传 `--launcher-package your.package.name`。

配置完成后，无需 Mac、网络在线、ADB 保持服务或网页服务器来维持 HOME。开机、中转自身升级时由电视接收系统事件检查。恢复出厂、清数据或卸载中转不在保存范围，不能承诺未来任何系统 OTA 均兼容。

## 4. 可选 ADB 保持

ADB 必须先在电视设置开启，并由电视确认电脑密钥。需要保持开关时，由设备所有者明确授予中转的开发权限：

```sh
adb -s "$TV_SERIAL" shell pm grant com.ts.launcher android.permission.WRITE_SECURE_SETTINGS
```

然后在桌面「更多设置 → 保持 ADB 开启」打开开关。中转和桌面需同签名。保持服务默认关闭；不会自动接受陌生电脑密钥、不会持有唤醒锁，也不能保证深度待机时网络仍可用。

关闭桌面开关即可停用服务。也可撤销权限：

```sh
adb -s "$TV_SERIAL" shell pm revoke com.ts.launcher android.permission.WRITE_SECURE_SETTINGS
```

不要为了此功能授予桌面 DUMP 或系统电源权限；桌面的导出配置入口以 DUMP 权限限制调用方，ADB shell 已具备相应访问能力。

## 5. 恢复

保留 `.local/` 中针对该设备的首次状态，执行：

```sh
python3 scripts/tv_setup.py --serial "$TV_SERIAL" restore
```

脚本先通过中转关闭自动重设并请求移除持久规则，再按原记录启用系统 HOME、设置原默认项，并等待实际解析结果符合原 HOME 后发送 HOME。旧中转没有策略端点时普通恢复仍可使用；记录过持久模式、却无法移除策略时会停止并要求修复中转，避免错误报告恢复成功。

### 手动恢复（原记录丢失）

先保留中转 APK 和签名，关闭电视端自动重设并移除它的规则：

```sh
adb -s "$TV_SERIAL" shell content call --uri content://com.ts.launcher.homepolicy --method disable
```

确认 `enabled=false`，且本次请求被接收、日志出现 `Removed our persistent HOME preference; uid=1000`。如果接口缺失而你曾启用持久模式，先重新安装自己保存的**相同签名**中转修复恢复入口；不要先卸载或清数据。固件已经撤销系统命令能力时，移除也可能失败，不能仅依据开关值判断成功。

确认原系统桌面包/组件属于当前型号：

```sh
adb -s "$TV_SERIAL" shell pm enable --user 0 com.mitv.tvhome
adb -s "$TV_SERIAL" shell cmd package query-activities --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
```

已测固件的系统 HOME 是 `com.mitv.tvhome/.MainActivityUserMode`；其他型号必须采用自己的查询结果或首次记录。用实际组件替换占位符：

```sh
export ORIGINAL_HOME='<ORIGINAL_HOME_COMPONENT>'
adb -s "$TV_SERIAL" shell cmd package set-home-activity --user 0 "$ORIGINAL_HOME"
adb -s "$TV_SERIAL" shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME
```

确认实际解析和实体 HOME 均回到原系统桌面后，再考虑移除第三方桌面/中转。**只改普通默认项不能覆盖仍存在的持久规则。** 不要先卸载当前唯一可用 HOME。停用桌面屏保时等待设置页完成“跟随系统”恢复，确保系统屏保入口已复原。
