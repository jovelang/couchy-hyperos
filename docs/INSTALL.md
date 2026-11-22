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

确认新桌面可直接启动、电脑仍能连接 ADB 后，再设置 HOME：

```sh
python3 scripts/tv_setup.py --serial "$TV_SERIAL" activate --bridge
```

脚本会把原 HOME 和包状态记录到本机被忽略的 `.local/`，并保留设置页回退。测试实体遥控 HOME、待机唤醒与正常重启。

只有在确认固件继续强制系统桌面接管、且恢复路径已验证后，才考虑可逆地停用小米系统 HOME：

```sh
python3 scripts/tv_setup.py --serial "$TV_SERIAL" activate --bridge \
  --disable-stock --stock-home-package com.mitv.tvhome
```

此命令不会卸载系统包。脚本只允许这个已知 Xiaomi HOME 包，不接受任意系统包名；首次记录不会被后续 activate 覆盖。其他厂商需另行验证，不要修改脚本去停用 SystemUI 或设置应用。

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

恢复会按记录重新启用原系统 HOME（仅当原先启用），再设置原 HOME。如果状态文件丢失，且你确定原来使用小米系统桌面，可手动重新启用并在电视中选择：

```sh
adb -s "$TV_SERIAL" shell pm enable --user 0 com.mitv.tvhome
adb -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME
```

恢复后再考虑移除第三方桌面/中转；不要先卸载当前唯一可用 HOME。停用桌面屏保时等待设置页完成“跟随系统”恢复，确保系统屏保入口已复原。
