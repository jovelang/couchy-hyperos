# SPDX-License-Identifier: GPL-3.0-only
# Couchy HyperOS remote companion, generalized 2026-11-23.
"""Local TV operations. No API accepts shell commands or host filesystem paths."""

import asyncio
import io
import json
import logging
import os
import re
import shlex
import time
import urllib.request
import uuid
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
DATA = Path(os.environ.get("COUCHY_DATA_DIR", str(ROOT / "data"))).expanduser()
DATA.mkdir(mode=0o700, exist_ok=True)
DEFAULT_PACKAGE = "io.github.jovelang.couchy"
ADB = os.environ.get("ADB", "adb")
KEYS = {
    "up": 19,
    "down": 20,
    "left": 21,
    "right": 22,
    "ok": 23,
    "back": 4,
    "home": 3,
    "menu": 82,
    "volume_up": 24,
    "volume_down": 25,
    "mute": 164,
    "play_pause": 85,
    "play": 126,
    "pause": 127,
    "next": 87,
    "previous": 88,
    "delete": 67,
}


class ControlError(Exception):
    def __init__(self, message, status=409):
        self.message, self.status = message, status
        super().__init__(message)


async def process(args, timeout=15, binary=False):
    proc = await asyncio.create_subprocess_exec(
        *map(str, args), stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
    )
    try:
        out, err = await asyncio.wait_for(proc.communicate(), timeout)
    except (asyncio.TimeoutError, asyncio.CancelledError) as error:
        if proc.returncode is None:
            proc.kill()
        await proc.communicate()
        if isinstance(error, asyncio.CancelledError):
            raise
        raise ControlError("操作超时，请检查电视是否在线", 504)
    if proc.returncode:
        detail = (out + err).decode("utf-8", "replace")[-800:]
        if any(t in detail for t in ["offline", "not found", "closed", "cannot connect"]):
            raise ControlError("电视暂时未连接，请先开机或稍后重试", 503)
        raise ControlError(detail or "电视未完成操作", 502)
    return out if binary else out.decode("utf-8", "replace")


class TVControl:
    def __init__(self, config):
        self.config = config
        self.serial = config["adb_serial"]
        self.package = config.get("launcher_package", DEFAULT_PACKAGE)
        self.provider_uri = f"content://{self.package}.config"
        self.ha_config = config.get("ha", {})
        self.model = config.get("model_name", "Android TV")
        self.status = {
            "state": "checking",
            "label": "正在连接",
            "foreground": None,
            "updated_at": None,
            "adb": False,
        }
        self.apps_cache = []
        self.apps_at = 0
        self.semaphore = asyncio.Semaphore(3)
        self.config_lock = asyncio.Lock()
        self.status_lock = asyncio.Lock()
        self.power_lock = asyncio.Lock()
        self.shot_lock = asyncio.Lock()
        self.shot = None
        self.shot_at = 0
        self.apk_path = None
        self.last_mijia = {}
        self.last_ha_at = 0
        self.last_confirmed_off = 0

    async def adb(self, *args, timeout=15, binary=False):
        async with self.semaphore:
            return await process([ADB, "-s", self.serial, *args], timeout, binary)

    async def shell(self, *args, timeout=15):
        # adb shell joins arguments; quote once for the remote shell, never on the host.
        return await self.adb("shell", shlex.join([str(x) for x in args]), timeout=timeout)

    async def provider(self, method, arg=None):
        args = ["content", "call", "--uri", self.provider_uri, "--method", method]
        if arg is not None:
            args += [
                "--arg",
                json.dumps(arg, ensure_ascii=False, separators=(",", ":")),
            ]
        return await self.shell(*args, timeout=20)

    async def get_config(self):
        text = await self.shell("content", "query", "--uri", self.provider_uri)
        try:
            return json.loads(text.split("json=", 1)[1])
        except (ValueError, IndexError):
            raise ControlError("桌面设置暂时不可读", 502)

    async def patch_config(self, patch):
        async with self.config_lock:
            await self.provider("patch", patch)
            return await self.get_config()

    async def poll(self):
        while True:
            try:
                await self.refresh_status()
            except Exception:
                self.status = {
                    **self.status,
                    "state": "offline",
                    "label": "暂未连接",
                    "adb": False,
                    "updated_at": time.time(),
                }
            await asyncio.sleep(5 if self.status.get("adb") else 8)

    async def ha_request(self, path, data=None):
        def invoke():
            try:
                token_file = Path(self.ha_config.get("token_file", "")).expanduser()
                if not token_file.is_file():
                    raise ValueError("Missing HA token file")
                token = token_file.read_text().strip()
                if not token:
                    raise ValueError("Empty HA token")
                base = self.ha_config.get("base_url", "").rstrip("/")
                if not base.startswith(("http://", "https://")):
                    raise ValueError("Missing HA URL")
                req = urllib.request.Request(
                    base + "/api/" + path,
                    data=json.dumps(data).encode() if data is not None else None,
                    headers={
                        "Authorization": "Bearer " + token,
                        "Content-Type": "application/json",
                    },
                )
                with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
                    req, timeout=10
                ) as response:
                    return json.load(response)
            except Exception as error:
                logging.getLogger(__name__).warning(
                    "HA request unavailable: %s", type(error).__name__
                )
                raise ControlError("米家通道暂时不可用，请检查服务器的 Home Assistant 配置", 502)

        return await asyncio.to_thread(invoke)

    async def ha_action(self, action):
        domain = action.get("domain", "")
        service = action.get("service", "")
        entity = action.get("entity_id", "")
        if not all(re.fullmatch(r"[a-z_]+", x) for x in [domain, service]) or not re.fullmatch(
            r"[a-z_]+\.[a-zA-Z0-9_]+", entity
        ):
            raise ControlError("Home Assistant 动作配置无效", 400)
        return await self.ha_request("services/" + domain + "/" + service, {"entity_id": entity})

    async def refresh_status(self):
        async with self.status_lock:
            return await self._refresh_status()

    async def _refresh_status(self):
        if time.time() - self.last_ha_at > 20:
            configured = bool(self.ha_config.get("base_url") and self.ha_config.get("token_file"))
            self.last_mijia = {
                "configured": configured,
                "available": False,
                "state": "unconfigured",
            }
            if configured:
                try:
                    entity = self.ha_config.get("state_entity")
                    value = await self.ha_request("states/" + entity if entity else "config")
                    state = value.get("state", "connected")
                    self.last_mijia = {
                        "configured": True,
                        "available": state not in ["unknown", "unavailable"],
                        "state": state,
                    }
                except ControlError:
                    self.last_mijia = {
                        "configured": True,
                        "available": False,
                        "state": "unavailable",
                    }
            self.last_ha_at = time.time()
        try:
            await process([ADB, "connect", self.serial], timeout=3)
            raw = await self.shell(
                "sh",
                "-c",
                "dumpsys power | grep -E 'mWakefulness=|mLastSleepReason='; dumpsys activity activities | grep -m 1 topResumedActivity || true",
                timeout=10,
            )
            awake = "mWakefulness=Awake" in raw
            if awake:
                self.last_confirmed_off = 0
            asleep = bool(re.search(r"mWakefulness=(Asleep|Dozing)", raw))
            panel_off = False
            if awake:
                screen = await self.shell(
                    "sh",
                    "-c",
                    "dumpsys SurfaceFlinger | grep -m 1 powerMode=",
                    timeout=10,
                )
                panel_off = "powerMode=Off" in screen
            foreground = None
            match = re.search(r"topResumedActivity=.*?\s([\w.]+)/([\w.$]+)", raw)
            if match and awake:
                pkg, activity = match.groups()
                label = next((x["label"] for x in self.apps_cache if x["package"] == pkg), None)
                label = label or {
                    self.package: "Couchy HyperOS",
                    "com.xiaomi.mitv.settings": "电视设置",
                    "com.xiaomi.mitv.calendar": "日历",
                }.get(pkg, pkg)
                if activity.endswith("ScreenSaverActivity"):
                    label = "桌面屏保"
                foreground = {"package": pkg, "activity": activity, "label": label}
            state = (
                "screen_off"
                if awake and panel_off
                else "on"
                if awake
                else "standby"
                if asleep
                else "unknown"
            )
            if state == "standby":
                self.last_confirmed_off = time.time()
            self.status = {
                "state": state,
                "label": {
                    "on": "已开机",
                    "screen_off": "屏幕已熄灭",
                    "standby": "待机中",
                    "unknown": "状态待确认",
                }[state],
                "foreground": foreground,
                "adb": True,
                "updated_at": time.time(),
                "mijia": self.last_mijia,
                "model": self.model,
            }
        except ControlError:
            known_off = self.last_mijia.get("state") == "off" or (
                self.last_confirmed_off and time.time() - self.last_confirmed_off < 86400
            )
            self.status = {
                "state": "standby" if known_off else "offline",
                "label": "待机中" if known_off else "离线，状态待确认",
                "foreground": None,
                "adb": False,
                "updated_at": time.time(),
                "mijia": self.last_mijia,
                "model": self.model,
                "certainty": "米家或上次确认的待机状态"
                if known_off
                else "未把网络离线直接判为关机",
            }
        return self.status

    async def require_awake(self):
        if self.status["state"] not in ["on", "screen_off"]:
            await self.refresh_status()
        if self.status["state"] not in ["on", "screen_off"]:
            raise ControlError("电视在待机或未连接，请先开机", 409)

    async def key(self, name, hold=False):
        if not isinstance(name, str) or name not in KEYS or type(hold) is not bool:
            raise ControlError("不支持的按键", 400)
        await self.require_awake()
        args = ["input", "keyevent"] + (["--longpress"] if hold else []) + [str(KEYS[name])]
        await self.shell(*args)

    async def screenshot(self, original=False):
        await self.require_awake()
        async with self.shot_lock:
            if self.shot is None or time.time() - self.shot_at > 1:
                self.shot = await self.adb("exec-out", "screencap", "-p", timeout=20, binary=True)
                if not self.shot.startswith(b"\x89PNG"):
                    raise ControlError("截图失败，请重试", 502)
                self.shot_at = time.time()
            if original:
                return self.shot, "image/png", self.shot_at

            def small():
                image = Image.open(io.BytesIO(self.shot)).convert("RGB")
                image.thumbnail((1280, 720))
                output = io.BytesIO()
                image.save(output, "JPEG", quality=84)
                return output.getvalue()

            return await asyncio.to_thread(small), "image/jpeg", self.shot_at

    async def apps(self, force=False):
        if force or not self.apps_cache or time.time() - self.apps_at > 60:
            text = await self.provider("remote-apps")
            try:
                self.apps_cache = json.loads(
                    re.search(r"apps_json=(.*)}\]\s*$", text, re.S).group(1)
                )
            except (ValueError, AttributeError):
                raise ControlError("应用列表暂时不可读，请稍后刷新", 502)
            self.apps_at = time.time()
        return self.apps_cache

    async def launch(self, pkg):
        await self.require_awake()
        self.check_package(pkg)
        app = next((x for x in await self.apps() if x["package"] == pkg), None)
        if not app:
            raise ControlError("没有找到这个应用的电视启动入口", 404)
        await self.shell(
            "am",
            "start",
            "--user",
            "0",
            "-n",
            app["component"],
            "-a",
            "android.intent.action.MAIN",
            "-f",
            "0x10200000",
        )

    @staticmethod
    def check_package(pkg):
        if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.]{1,180}", pkg):
            raise ControlError("应用包名无效", 400)

    async def shortcut(self, action):
        await self.require_awake()
        if action in ["desktop", "inputs"]:
            await self.shell(
                "am",
                "start",
                "-n",
                self.package + "/com.conreo.couchytv.MainActivity",
                "-a",
                self.package + ".REMOTE_ACTION",
                "--ei",
                "remote_action",
                "3" if action == "desktop" else "1",
            )
        elif action == "television":
            await self.shell(
                "am",
                "start",
                "-n",
                self.package + "/com.conreo.couchytv.MainActivity",
                "-a",
                self.package + ".REMOTE_ACTION",
                "--ei",
                "remote_action",
                "2",
            )
        elif action in ["wifi", "display", "sound"]:
            intent = {
                "wifi": "android.settings.WIFI_SETTINGS",
                "display": "android.settings.DISPLAY_SETTINGS",
                "sound": "android.settings.SOUND_SETTINGS",
            }[action]
            result = await self.shell("am", "start", "-a", intent)
            if "Error:" in result:
                await self.shortcut("television")
        else:
            raise ControlError("设置入口无效", 400)

    async def bridge(self, mode, path, destination=None):
        if not self.apk_path:
            self.apk_path = (
                (await self.shell("pm", "path", self.package))
                .splitlines()[0]
                .removeprefix("package:")
                .strip()
            )
            if not re.fullmatch(r"/data/app/[a-zA-Z0-9/_~.+=-]+\.apk", self.apk_path):
                raise ControlError("桌面服务路径不可用", 502)
        try:
            args = [
                "env",
                "CLASSPATH=" + self.apk_path,
                "app_process",
                "/system/bin",
                "com.conreo.couchytv.FileBridge",
                mode,
                path,
            ]
            if destination is not None:
                args.append(destination)
            raw = await self.shell(*args)
            line = next(l for l in raw.splitlines() if l.startswith("{"))
            result = json.loads(line)
            if result.get("error"):
                raise ControlError(result["error"], 400)
            return result
        except (ValueError, StopIteration):
            self.apk_path = None
            raise ControlError("文件服务暂未就绪，请刷新后重试", 502)
        except ControlError:
            self.apk_path = None
            raise

    async def canonical(self, path, directory=False):
        if (
            not isinstance(path, str)
            or not path.startswith("/sdcard")
            or "\x00" in path
            or len(path) > 1024
        ):
            raise ControlError("只能访问电视内部共享存储", 400)
        result = await self.bridge("check", path)
        if directory and not result.get("directory"):
            raise ControlError("目标不是文件夹", 400)
        return result["path"]

    async def power(self, on, job):
        async with self.power_lock:
            return await self._power(on, job)

    async def _power(self, on, job):
        await self.refresh_status()
        if on:
            if self.status["state"] == "on":
                return {"message": "电视已经开机"}
            if self.status["state"] == "screen_off":
                job["message"] = "正在通过米家点亮屏幕"
                await self.key("home")
                await asyncio.sleep(2)
                await self.refresh_status()
                if self.status["state"] == "on":
                    return {"message": "屏幕已点亮"}
            job["message"] = "正在通过米家开机"
            attempts = self.ha_config.get("power_on", [])
            if not attempts:
                raise ControlError("请先配置服务器的米家开机动作（ha.power_on）", 409)
            for action in attempts:
                try:
                    await self.ha_action(action)
                except ControlError:
                    continue
                for _ in range(12):
                    await asyncio.sleep(2)
                    await self.refresh_status()
                    if self.status["state"] == "on":
                        self.last_confirmed_off = 0
                        return {"message": "电视已开机"}
                    # Home is also the existing ADB keeper recovery entry.
                    if not self.status.get("adb"):
                        try:
                            if self.ha_config.get("home_action"):
                                await self.ha_action(self.ha_config["home_action"])
                        except ControlError:
                            pass
            raise ControlError("已发送米家开机指令，但尚未确认电视上线", 504)
        else:
            if self.status["state"] == "standby":
                return {"message": "电视已经待机"}
            if self.status["state"] == "offline":
                raise ControlError("电视未连接，无法确认是否需要关机", 409)
            job["message"] = "正在通过米家关机"
            actions = self.ha_config.get("power_off", [])
            if not actions:
                raise ControlError("请先配置服务器的米家关机动作（ha.power_off）", 409)
            await self.ha_action(actions[0])
            for _ in range(15):
                await asyncio.sleep(2)
                await self.refresh_status()
                if self.status["state"] == "standby":
                    return {"message": "电视已待机"}
            raise ControlError("米家已接受指令，但电视还没有进入待机，请查看截图确认电源选项", 504)


class Jobs:
    def __init__(self):
        self.items = {}
        self.tasks = set()
        path = DATA / "jobs.json"
        if path.exists():
            try:
                self.items = json.loads(path.read_text())
                for job in self.items.values():
                    if job["state"] in ["running", "queued"]:
                        job.update(state="error", message="服务重启，任务已中断，请重新提交")
            except (ValueError, KeyError):
                self.items = {}

    def save(self):
        self.items = dict(sorted(self.items.items(), key=lambda x: x[1]["created_at"])[-100:])
        tmp = DATA / "jobs.tmp"
        tmp.write_text(json.dumps(self.items, ensure_ascii=False))
        os.chmod(tmp, 0o600)
        tmp.replace(DATA / "jobs.json")

    def create(self, kind, name, operation):
        if sum(j["state"] in ["queued", "running"] for j in self.items.values()) >= 4:
            raise ControlError("已有任务正在执行，请稍后再试", 429)
        ident = uuid.uuid4().hex
        job = {
            "id": ident,
            "kind": kind,
            "name": name,
            "state": "queued",
            "message": "等待执行",
            "created_at": time.time(),
        }
        self.items[ident] = job
        self.save()

        async def work():
            job.update(state="running", message="正在处理")
            self.save()
            try:
                job["result"] = await operation(job)
                job.update(state="done", message=job["result"].get("message", "已完成"))
            except ControlError as e:
                job.update(state="error", message=e.message)
            except Exception:
                job.update(state="error", message="任务未完成，请重试或检查连接")
            finally:
                job["finished_at"] = time.time()
                self.save()

        task = asyncio.create_task(work())
        self.tasks.add(task)
        task.add_done_callback(self.tasks.discard)
        return job
