#!/usr/bin/env python3
"""Inspect HOME support and keep a per-device rollback record. No device is hardcoded."""

# SPDX-License-Identifier: GPL-3.0-only
import argparse
import hashlib
import json
import os
import re
import shlex
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.jovelang.couchy"
BRIDGE = "com.ts.launcher/com.homekey.standin.MainActivity"
COMPONENT = re.compile(r"[a-zA-Z][a-zA-Z0-9_.]*/[a-zA-Z0-9_.$]+")


class TV:
    def __init__(self, serial):
        if not serial or serial.startswith("-"):
            raise ValueError("Invalid device serial")
        self.serial = serial

    def shell(self, *args):
        result = subprocess.run(
            [
                os.environ.get("ADB", "adb"),
                "-s",
                self.serial,
                "shell",
                shlex.join(args),
            ],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode or "Error:" in result.stdout or "Exception" in result.stdout:
            raise RuntimeError(
                "ADB operation failed; check authorization and the TV's on-screen state"
            )
        return result.stdout.strip()

    def home(self):
        result = self.shell(
            "cmd",
            "package",
            "resolve-activity",
            "--brief",
            "-a",
            "android.intent.action.MAIN",
            "-c",
            "android.intent.category.HOME",
        )
        return next(
            (
                line.strip()
                for line in reversed(result.splitlines())
                if COMPONENT.fullmatch(line.strip())
            ),
            None,
        )

    def installed(self, package):
        return "package:" + package in self.shell("pm", "list", "packages", package).splitlines()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--launcher-package", default=PACKAGE)
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("doctor")
    activate = sub.add_parser("activate")
    activate.add_argument("--bridge", action="store_true")
    activate.add_argument("--disable-stock", action="store_true")
    activate.add_argument("--stock-home-package", default="com.mitv.tvhome")
    sub.add_parser("restore")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.]{1,180}", args.launcher_package):
        parser.error("Invalid launcher package")
    tv = TV(args.serial)
    state_path = (
        ROOT
        / ".local"
        / ("home-" + hashlib.sha256(args.serial.encode()).hexdigest()[:12] + ".json")
    )
    if args.action == "doctor":
        print(
            json.dumps(
                {
                    "model": tv.shell("getprop", "ro.product.model"),
                    "android": tv.shell("getprop", "ro.build.version.release"),
                    "firmware": tv.shell("getprop", "ro.build.display.id"),
                    "home": tv.home(),
                    "launcher_installed": tv.installed(args.launcher_package),
                    "bridge_installed": tv.installed("com.ts.launcher"),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return
    if args.action == "restore":
        if not state_path.exists():
            raise RuntimeError("No rollback record. See docs/INSTALL.md for manual recovery.")
        state = json.loads(state_path.read_text())
        if state.get("serial") != args.serial:
            raise RuntimeError("Rollback record belongs to another device")
        original = state.get("home")
        if not original or not COMPONENT.fullmatch(original):
            raise RuntimeError("Original HOME is not a valid explicit component")
        if not state["stock_disabled"] and tv.installed(state["stock_package"]):
            tv.shell("pm", "enable", "--user", "0", state["stock_package"])
        tv.shell("cmd", "package", "set-home-activity", original)
        tv.shell("input", "keyevent", "KEYCODE_HOME")
        print(
            "Restored original HOME. Confirm with the physical remote before uninstalling anything."
        )
        return
    if args.stock_home_package != "com.mitv.tvhome":
        parser.error(
            "Only the known Xiaomi stock HOME may be disabled. Other vendors require a reviewed port."
        )
    if args.disable_stock and not args.bridge:
        parser.error("Test the HOME bridge before considering stock HOME changes")
    if args.disable_stock and not tv.installed(args.stock_home_package):
        raise RuntimeError("The specified Xiaomi stock HOME is not installed")
    if not tv.installed(args.launcher_package):
        raise RuntimeError("Install and open the launcher first")
    target = BRIDGE if args.bridge else args.launcher_package + "/com.conreo.couchytv.MainActivity"
    if args.bridge and not tv.installed("com.ts.launcher"):
        raise RuntimeError("Install the matching HOME bridge first")
    original = tv.home()
    if not original:
        raise RuntimeError("Cannot record the original HOME; leaving settings untouched")
    if original == target and not state_path.exists():
        raise RuntimeError(
            "Already using target HOME with no baseline. Restore the original HOME before recording a new baseline."
        )
    if not state_path.exists():
        disabled = tv.shell("pm", "list", "packages", "-d").splitlines()
        state = {
            "serial": args.serial,
            "home": original,
            "stock_package": args.stock_home_package,
            "stock_disabled": "package:" + args.stock_home_package in disabled,
        }
        state_path.parent.mkdir(mode=0o700, exist_ok=True)
        descriptor = os.open(state_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "w") as handle:
            json.dump(state, handle, indent=2)
    tv.shell("am", "start", "-n", args.launcher_package + "/com.conreo.couchytv.MainActivity")
    tv.shell("cmd", "package", "set-home-activity", target)
    if args.disable_stock:
        tv.shell("pm", "disable-user", "--user", "0", args.stock_home_package)
    print(
        "HOME setting applied; rollback record saved locally. Verify physical HOME, restart and wake, as described in docs/INSTALL.md."
    )


if __name__ == "__main__":
    try:
        main()
    except (
        ValueError,
        RuntimeError,
        subprocess.TimeoutExpired,
        FileNotFoundError,
    ) as error:
        raise SystemExit(str(error))
