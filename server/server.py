# SPDX-License-Identifier: GPL-3.0-only
import asyncio
import hashlib
import hmac
import ipaddress
import json
import os
import re
import secrets
import shutil
import time
import urllib.parse
import zipfile
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, Request, UploadFile, File, Form
from fastapi.responses import JSONResponse, FileResponse, Response
from fastapi.staticfiles import StaticFiles
from core import ROOT, DATA, TVControl, Jobs, ControlError, process
from configuration import load_config
from settings_schema import schema, validate

CONFIG = load_config()
control = TVControl(CONFIG)
jobs = Jobs()
attempts = defaultdict(deque)
write_lock = asyncio.Lock()
transfers = DATA / "transfers"
transfers.mkdir(mode=0o700, exist_ok=True)
APK_TOOL = os.environ.get("AAPT") or shutil.which("aapt")
COOKIE = "couchy_remote_session"
STARTED_AT = time.time()


@asynccontextmanager
async def lifespan(app):
    task = asyncio.create_task(control.poll())
    cleaner = asyncio.create_task(clean_transfers())
    yield
    task.cancel()
    cleaner.cancel()
    for t in list(jobs.tasks):
        t.cancel()
    await asyncio.gather(task, cleaner, *jobs.tasks, return_exceptions=True)


async def clean_transfers():
    while True:
        # Only server-owned staging folders expire; files already sent to the TV remain.
        for folder in transfers.iterdir():
            if (
                re.fullmatch(r"[0-9a-f]{32}", folder.name)
                and folder.is_dir()
                and not folder.is_symlink()
                and time.time() - folder.stat().st_mtime > 86400
            ):
                await asyncio.to_thread(shutil.rmtree, folder, True)
        await asyncio.sleep(3600)


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


def session(request):
    auth = request.headers.get("authorization", "")
    if auth.startswith("Bearer ") and hmac.compare_digest(auth[7:], CONFIG["admin_token"]):
        return True
    try:
        value = request.cookies.get(COOKIE, "")
        payload, sign = value.rsplit(".", 1)
        expected = hmac.new(CONFIG["secret"].encode(), payload.encode(), hashlib.sha256).hexdigest()
        expiry = int(payload.split(":", 1)[0])
        return hmac.compare_digest(sign, expected) and time.time() < expiry
    except (ValueError, TypeError):
        return False


@app.middleware("http")
async def local_guard(request, call_next):
    peer = request.client.host if request.client else ""
    try:
        address = ipaddress.ip_address(peer)
        networks = [
            "127.0.0.0/8",
            "192.168.0.0/16",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "::1/128",
            "fc00::/7",
        ]
        if not any(address in ipaddress.ip_network(n) for n in networks):
            return JSONResponse({"error": "仅支持局域网访问"}, 403)
    except ValueError:
        return JSONResponse({"error": "来源地址无效"}, 403)
    host = urllib.parse.urlsplit("//" + request.headers.get("host", "")).hostname
    if host not in CONFIG["allowed_hosts"]:
        return JSONResponse({"error": "访问地址无效"}, 400)
    path = request.url.path
    if request.method not in ["GET", "HEAD", "OPTIONS"]:
        origin = request.headers.get("origin")
        bearer = request.headers.get("authorization", "").startswith("Bearer ")
        if not bearer and origin != str(request.base_url).rstrip("/"):
            return JSONResponse({"error": "请从本机控制网页提交操作"}, 403)
    if (
        path.startswith("/api/")
        and path not in ["/api/login", "/api/session"]
        and not session(request)
    ):
        return JSONResponse({"error": "请先输入连接码"}, 401)
    try:
        response = await call_next(request)
    except ControlError as e:
        return JSONResponse({"error": e.message}, e.status)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Content-Security-Policy"] = (
        "default-src 'self'; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
    )
    response.headers["Cache-Control"] = "no-store" if path.startswith("/api/") else "no-cache"
    return response


@app.exception_handler(ControlError)
async def control_error(request, error):
    return JSONResponse({"error": error.message}, error.status)


async def object_body(request):
    try:
        value = await request.json()
    except ValueError:
        raise ControlError("请求内容无效", 400)
    if not isinstance(value, dict):
        raise ControlError("请求内容无效", 400)
    return value


@app.get("/health")
async def health():
    return {
        "ok": True,
        "service": "couchy-remote",
        "version": 1,
        "started_at": STARTED_AT,
    }


@app.get("/api/session")
async def who(request: Request):
    return {"authenticated": session(request)}


@app.post("/api/login")
async def login(request: Request):
    key = request.client.host
    now = time.time()
    history = attempts[key]
    while history and now - history[0] > 60:
        history.popleft()
    if len(history) >= 8:
        return JSONResponse(
            {"error": "尝试次数过多，请一分钟后再试"},
            429,
            headers={"Retry-After": "60"},
        )
    history.append(now)
    raw = bytearray()
    async for part in request.stream():
        raw.extend(part)
        if len(raw) > 512:
            raise ControlError("请求过大", 413)
    try:
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError()
        pin = re.sub(r"\s", "", str(value.get("code", "")))
    except ValueError:
        raise ControlError("请输入 8 位连接码", 400)
    if not re.fullmatch(r"[0-9]{8}", pin) or not hmac.compare_digest(pin, CONFIG["pair_code"]):
        raise ControlError("连接码不正确", 401)
    history.clear()
    payload = str(int(time.time() + 7 * 86400)) + ":" + secrets.token_urlsafe(24)
    sign = hmac.new(CONFIG["secret"].encode(), payload.encode(), hashlib.sha256).hexdigest()
    response = JSONResponse({"ok": True})
    response.set_cookie(
        COOKIE,
        payload + "." + sign,
        httponly=True,
        samesite="strict",
        max_age=7 * 86400,
        path="/",
    )
    return response


@app.post("/api/logout")
async def logout():
    r = JSONResponse({"ok": True})
    r.delete_cookie(COOKIE, path="/")
    return r


@app.get("/api/status")
async def status():
    return control.status


@app.post("/api/key")
async def key(request: Request):
    body = await object_body(request)
    await control.key(body.get("key"), body.get("hold", False))
    return {"ok": True}


@app.post("/api/shortcut")
async def shortcut(request: Request):
    body = await object_body(request)
    await control.shortcut(body.get("action"))
    return {"ok": True}


@app.post("/api/power")
async def power(request: Request):
    body = await object_body(request)
    value = body.get("on")
    if type(value) is not bool:
        raise ControlError("电源指令无效", 400)
    return public_job(
        jobs.create("power", "开机" if value else "关机", lambda job: control.power(value, job))
    )


@app.get("/api/screenshot")
async def screenshot(original: bool = False):
    data, kind, taken = await control.screenshot(original)
    return Response(
        data,
        media_type=kind,
        headers={
            "X-Captured-At": str(taken),
            **(
                {"Content-Disposition": 'attachment; filename="tv-screenshot.png"'}
                if original
                else {}
            ),
        },
    )


@app.get("/api/apps")
async def apps():
    values = await control.apps()
    cfg = await control.get_config()
    return [{**x, "hidden": x["package"] in cfg["hidden"]} for x in values]


@app.get("/api/apps/{pkg}/icon")
async def icon(pkg: str):
    control.check_package(pkg)
    cache = DATA / "icons"
    cache.mkdir(mode=0o700, exist_ok=True)
    file = cache / (pkg + ".png")
    if not file.exists() or time.time() - file.stat().st_mtime > 86400:
        data = await control.adb(
            "exec-out",
            "content",
            "read",
            "--uri",
            control.provider_uri + "/artwork/" + pkg + "/icon",
            binary=True,
        )
        if not data.startswith(b"\x89PNG"):
            raise ControlError("图标不可用", 404)
        file.write_bytes(data)
    return FileResponse(
        file, media_type="image/png", headers={"Cache-Control": "private, max-age=3600"}
    )


@app.post("/api/apps/{pkg}/launch")
async def launch(pkg: str):
    await control.launch(pkg)
    return {"ok": True}


@app.post("/api/apps/{pkg}/visibility")
async def visibility(pkg: str, request: Request):
    control.check_package(pkg)
    body = await object_body(request)
    if type(body.get("hidden")) is not bool:
        raise ControlError("选项无效", 400)
    async with control.config_lock:
        cfg = await control.get_config()
        hidden = set(cfg["hidden"])
        if body["hidden"]:
            hidden.add(pkg)
        else:
            hidden.discard(pkg)
        await control.provider("patch", {"hidden": sorted(hidden)})
    return {"ok": True}


@app.get("/api/config/schema")
async def config_schema():
    return schema()


@app.get("/api/config")
async def get_config():
    cfg = await control.get_config()
    return {k: v for k, v in cfg.items() if not k.startswith("remoteControl")}


@app.patch("/api/config")
async def patch_config(request: Request):
    patch = validate(await object_body(request))
    cfg = await control.patch_config(patch)
    return {k: v for k, v in cfg.items() if not k.startswith("remoteControl")}


@app.get("/api/files")
async def files(path: str = "/sdcard"):
    path = await control.canonical(path, directory=True)
    return await control.bridge("list", path)


async def receive(file: UploadFile, apk=False):
    name = file.filename or "file"
    if (
        "/" in name
        or "\\" in name
        or name in [".", ".."]
        or any(ord(c) < 32 for c in name)
        or len(name) > 200
    ):
        raise ControlError("文件名无效", 400)
    if apk and not name.lower().endswith(".apk"):
        raise ControlError("请选择 APK 文件", 400)
    ident = secrets.token_hex(16)
    folder = transfers / ident
    folder.mkdir(mode=0o700)
    local = folder / ("package.apk" if apk else "upload.bin")
    size = 0
    digest = hashlib.sha256()
    limit = 1024**3 if apk else 2 * 1024**3
    try:
        with local.open("wb") as out:
            os.chmod(local, 0o600)
            while chunk := await file.read(1024**2):
                size += len(chunk)
                if size > limit:
                    raise ControlError("APK 最大 1 GB，普通文件最大 2 GB", 413)
                if shutil.disk_usage(DATA).free < 256 * 1024**2:
                    raise ControlError("服务设备可用空间不足", 507)
                digest.update(chunk)
                out.write(chunk)
        if size == 0:
            raise ControlError("文件为空", 400)
        if apk:
            try:
                with zipfile.ZipFile(local) as z:
                    if "AndroidManifest.xml" not in z.namelist():
                        raise ValueError()
            except (ValueError, zipfile.BadZipFile):
                raise ControlError("不是有效的 APK 文件", 400)
        return local, name, size, digest.hexdigest()
    except BaseException:
        shutil.rmtree(folder, ignore_errors=True)
        raise
    finally:
        await file.close()


@app.post("/api/files/upload")
async def upload(file: UploadFile = File(...), path: str = Form("/sdcard/Download")):
    path = await control.canonical(path, directory=True)
    local, name, size, digest = await receive(file)

    async def operation(job):
        async with write_lock:
            target = path.rstrip("/") + "/" + name
            if (await control.bridge("check", target))["exists"]:
                f = Path(name)
                target = path.rstrip("/") + "/" + f.stem + "-" + job["id"][:6] + f.suffix
            temp = path.rstrip("/") + "/.couchy-transfer-" + job["id"] + ".part"
            try:
                job["message"] = "正在传送到电视"
                await control.adb("push", local, temp, timeout=600)
                info = await control.bridge("check", temp)
                if info["size"] != size:
                    raise ControlError("传送大小不一致，请重试", 502)
                if size <= 64 * 1024**2:
                    job["message"] = "正在校验文件"
                    remote = (await control.shell("sha256sum", temp, timeout=60)).split()[0]
                    if remote != digest:
                        raise ControlError("文件校验未通过，请重试", 502)
                await control.bridge("commit", temp, target)
                return {
                    "message": "文件已传到电视",
                    "path": target,
                    "size": size,
                    "sha256": digest,
                }
            finally:
                try:
                    await control.shell("rm", "-f", temp)
                except ControlError:
                    pass
                local.unlink(missing_ok=True)

    try:
        return public_job(jobs.create("upload", name, operation))
    except BaseException:
        shutil.rmtree(local.parent, ignore_errors=True)
        raise


@app.post("/api/files/download")
async def download(request: Request):
    path = await control.canonical((await object_body(request)).get("path"))
    info = await control.bridge("check", path)
    if not info["exists"] or info["directory"]:
        raise ControlError("请选择一个文件", 400)
    if info["size"] > 2 * 1024**3:
        raise ControlError("单个文件最大支持 2 GB", 413)

    async def operation(job):
        folder = transfers / job["id"]
        folder.mkdir(mode=0o700)
        target = folder / "download.bin"
        job["message"] = "正在从电视读取文件"
        await control.adb("pull", path, target, timeout=600)
        if target.stat().st_size != info["size"]:
            raise ControlError("文件在传送过程中发生变化，请重试", 502)
        return {
            "message": "文件已准备好",
            "download": "/api/jobs/" + job["id"] + "/file",
            "name": Path(path).name,
            "size": info["size"],
            "_file": str(target),
        }

    return public_job(jobs.create("download", Path(path).name, operation))


@app.post("/api/install")
async def install(file: UploadFile = File(...)):
    if not APK_TOOL:
        raise ControlError(
            "请在服务器安装 Android SDK Build Tools，并设置 AAPT 或将 aapt 加入 PATH",
            503,
        )
    local, name, size, digest = await receive(file, apk=True)

    async def operation(job):
        remote = "/data/local/tmp/couchy-remote-" + job["id"] + ".apk"
        try:
            job["message"] = "正在检查安装包"
            badging = await process([APK_TOOL, "dump", "badging", local], timeout=20)
            m = re.search(
                r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'",
                badging,
            )
            if not m:
                raise ControlError("无法读取 APK 信息", 400)
            pkg, code, version = m.groups()
            control.check_package(pkg)
            job["message"] = "正在传送到电视"
            await control.adb("push", local, remote, timeout=600)
            checksum = (await control.shell("sha256sum", remote, timeout=60)).split()[0]
            if checksum != digest:
                raise ControlError("安装包传送校验未通过", 502)
            job["message"] = "正在安装"
            result = await control.shell("pm", "install", "-r", remote, timeout=180)
            if "Success" not in result:
                raise ControlError("安装失败：" + result.strip()[-350:], 400)
            control.apps_at = 0
            control.apk_path = None
            return {
                "message": "安装成功",
                "package": pkg,
                "version": version,
                "versionCode": code,
                "sha256": digest,
            }
        finally:
            try:
                await control.shell("rm", "-f", remote)
            except ControlError:
                pass
            local.unlink(missing_ok=True)

    try:
        return public_job(jobs.create("install", name, operation))
    except BaseException:
        shutil.rmtree(local.parent, ignore_errors=True)
        raise


def public_job(job):
    return {
        k: ({n: v for n, v in value.items() if not n.startswith("_")} if k == "result" else value)
        for k, value in job.items()
        if not k.startswith("_")
    }


@app.get("/api/jobs")
async def history():
    return [
        public_job(x)
        for x in sorted(jobs.items.values(), key=lambda j: j["created_at"], reverse=True)[:50]
    ]


@app.get("/api/jobs/{ident}")
async def job(ident: str):
    if ident not in jobs.items:
        raise ControlError("任务不存在", 404)
    return public_job(jobs.items[ident])


@app.get("/api/jobs/{ident}/file")
async def job_file(ident: str):
    j = jobs.items.get(ident, {})
    result = j.get("result", {})
    if j.get("state") != "done" or not result.get("_file"):
        raise ControlError("文件尚未准备好", 404)
    file = Path(result["_file"])
    if not file.is_relative_to(transfers) or not file.exists():
        raise ControlError("下载文件已过期，请重新读取", 404)
    return FileResponse(file, filename=result["name"], media_type="application/octet-stream")


app.mount("/", StaticFiles(directory=ROOT / "static", html=True), name="web")
