"""Installation-specific values; this module contains no device or account credentials."""

# SPDX-License-Identifier: GPL-3.0-only
import json
import os
import re
import secrets
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
DEFAULT_PACKAGE = "io.github.jovelang.couchy"


def config_path():
    return Path(os.environ.get("COUCHY_CONFIG", str(ROOT / "config.json"))).expanduser()


def new_config(serial, url, lan_url=None, listen_host="127.0.0.1"):
    urls = [url.rstrip("/"), (lan_url or url).rstrip("/")]
    hosts = ["localhost", "127.0.0.1"]
    for value in urls:
        parsed = urlsplit(value)
        if (
            parsed.scheme != "http"
            or not parsed.hostname
            or parsed.username
            or parsed.password
            or parsed.path not in ("", "/")
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError("Use an HTTP origin such as http://tv-control.local:8768")
        if parsed.hostname not in hosts:
            hosts.append(parsed.hostname)
    return {
        "adb_serial": serial,
        "launcher_package": DEFAULT_PACKAGE,
        "listen_host": listen_host,
        "listen_port": urlsplit(urls[0]).port or 80,
        "url": urls[0],
        "lan_url": urls[1],
        "allowed_hosts": hosts,
        "pair_code": f"{secrets.randbelow(100_000_000):08d}",
        "secret": secrets.token_hex(32),
        "admin_token": secrets.token_hex(32),
        "ha": {},
    }


def load_config(path=None):
    path = Path(path) if path else config_path()
    if not path.is_file():
        raise ValueError(
            "Configuration missing. Run server/init.py first; see docs/REMOTE_CONTROL.md."
        )
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("Configuration must be a JSON object")
    if (
        not isinstance(value.get("adb_serial"), str)
        or not value["adb_serial"]
        or value["adb_serial"].startswith("-")
    ):
        raise ValueError("Set adb_serial to the TV's authorized adb device serial")
    if not re.fullmatch(
        r"[a-zA-Z][a-zA-Z0-9_.]{1,180}", value.get("launcher_package", DEFAULT_PACKAGE)
    ):
        raise ValueError("Invalid launcher_package")
    if not re.fullmatch(r"[0-9]{8}", value.get("pair_code", "")):
        raise ValueError("Generate an 8-digit connection code with init.py")
    for key in ["secret", "admin_token"]:
        if not re.fullmatch(r"[a-f0-9]{64}", value.get(key, "")):
            raise ValueError("Generate installation secrets with init.py")
    hosts = value.get("allowed_hosts", [])
    if (
        not isinstance(hosts, list)
        or not hosts
        or any(not isinstance(h, str) or not h or "/" in h or "*" in h for h in hosts)
    ):
        raise ValueError("allowed_hosts must contain explicit hostnames or IP addresses")
    for key in ["url", "lan_url"]:
        if urlsplit(value.get(key, "")).hostname not in hosts:
            raise ValueError(f"{key} must be included in allowed_hosts")
    port = value.get("listen_port", 8768)
    if type(port) is not int or not 1 <= port <= 65535:
        raise ValueError("Invalid listen_port")
    if not isinstance(value.get("ha", {}), dict):
        raise ValueError("ha must be a configuration object")
    return value
