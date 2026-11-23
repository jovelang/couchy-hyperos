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
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.jovelang.couchy"
BRIDGE = "com.ts.launcher/com.homekey.standin.MainActivity"
POLICY_URI = "content://com.ts.launcher.homepolicy"
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

    def policy(self, method):
        result = self.shell("content", "call", "--uri", POLICY_URI, "--method", method)
        if not re.search(r"\benabled=(?:true|false)\b", result):
            raise RuntimeError("HOME policy endpoint unavailable; install the matching bridge")
        return result

    def wait_home(self, target):
        target = expand_component(target)
        for _ in range(15):
            if expand_component(self.home()) == target:
                return
            time.sleep(1)
        raise RuntimeError(
            "HOME verification failed; keep the bridge installed and use the recovery guide"
        )


def expand_component(component):
    if not component or not COMPONENT.fullmatch(component):
        return None
    package, activity = component.split("/", 1)
    return package + "/" + (package + activity if activity.startswith(".") else activity)


def restore(tv, state):
    if state.get("serial") != tv.serial:
        raise RuntimeError("Rollback record belongs to another device")
    original = state.get("home")
    stock = state.get("stock_package")
    if not expand_component(original) or not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.]+", stock or ""):
        raise RuntimeError("Invalid rollback component/package")
    # Old records predate this feature. If the endpoint exists, remove its policy
    # even when persistent mode was enabled manually after the record was saved.
    if tv.installed("com.ts.launcher"):
        try:
            status = tv.policy("status")
        except RuntimeError:
            if state.get("persistent_requested"):
                raise RuntimeError(
                    "Cannot remove the recorded persistent policy; keep the bridge installed"
                ) from None
        else:
            if "enabled=true" in status or state.get("persistent_requested"):
                result = tv.policy("disable")
                if "lastCommandAccepted=true" not in result:
                    raise RuntimeError("Policy removal was not accepted; keep the bridge installed")
    elif state.get("persistent_requested"):
        raise RuntimeError("Reinstall the same signed bridge to remove its persistent policy first")
    if not state["stock_disabled"] and tv.installed(stock):
        tv.shell("pm", "enable", "--user", "0", stock)
    tv.shell("cmd", "package", "set-home-activity", "--user", "0", original)
    # Removal is asynchronous: do not report recovery until original HOME wins.
    tv.wait_home(original)
    tv.shell("input", "keyevent", "KEYCODE_HOME")


def activate(tv, state_path, launcher_package, use_bridge=False, persistent=False):
    if persistent:
        if not use_bridge:
            raise ValueError("Persistent HOME requires --bridge")
        if (
            tv.shell("am", "get-current-user") != "0"
            or tv.shell("getprop", "ro.build.version.sdk") != "34"
        ):
            raise RuntimeError("Persistent HOME is validated only for Android 14 user 0")
        libraries = tv.shell("pm", "list", "libraries").splitlines()
        if not {"library:com.xiaomi.mitv.api", "library:mitvmiddlewareimpl"}.issubset(libraries):
            raise RuntimeError("Required Xiaomi libraries unavailable; leaving HOME unchanged")
        tv.policy("status")
    if not tv.installed(launcher_package):
        raise RuntimeError("Install and open the launcher first")
    target = BRIDGE if use_bridge else launcher_package + "/com.conreo.couchytv.MainActivity"
    if use_bridge and not tv.installed("com.ts.launcher"):
        raise RuntimeError("Install the matching HOME bridge first")
    original = tv.home()
    if not expand_component(original) or original.split("/", 1)[0] == "android":
        raise RuntimeError("Original HOME is not a usable recovery target; select it first")
    if expand_component(original) == expand_component(target) and not state_path.exists():
        raise RuntimeError(
            "Already using target HOME with no baseline; see manual recovery in docs/INSTALL.md"
        )
    if state_path.exists():
        state = json.loads(state_path.read_text())
        if state.get("serial") != tv.serial:
            raise RuntimeError("Rollback record belongs to another device")
    else:
        disabled = tv.shell("pm", "list", "packages", "-d").splitlines()
        state = {
            "serial": tv.serial,
            "home": original,
            "stock_package": "com.mitv.tvhome",
            "stock_disabled": "package:com.mitv.tvhome" in disabled,
        }
        state_path.parent.mkdir(mode=0o700, exist_ok=True)
        descriptor = os.open(state_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, "w") as handle:
            json.dump(state, handle, indent=2)
    tv.shell("am", "start", "-n", launcher_package + "/com.conreo.couchytv.MainActivity")
    if persistent:
        # Persist intent before the asynchronous system request, including failures.
        state["persistent_requested"] = True
        state_path.write_text(json.dumps(state, indent=2))
        result = tv.policy("enable")
        if "lastCommandAccepted=true" not in result:
            raise RuntimeError(
                "Native request declined; run restore before retrying; original HOME was not disabled"
            )
        # Keep the stock desktop available, including when migrating from the old
        # disable-user approach. The saved original package state is not overwritten.
        if tv.installed("com.mitv.tvhome"):
            tv.shell("pm", "enable", "--user", "0", "com.mitv.tvhome")
        # Do not create an ordinary HOME preference that could mask a failed
        # persistent request during this check.
    else:
        tv.shell("cmd", "package", "set-home-activity", "--user", "0", target)
    tv.wait_home(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--launcher-package", default=PACKAGE)
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("doctor")
    setup = sub.add_parser("activate")
    setup.add_argument("--bridge", action="store_true")
    setup.add_argument(
        "--persistent",
        action="store_true",
        help="Opt into the Xiaomi TV-native policy (Android 14 user 0)",
    )
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
        restore(tv, state)
        print(
            "Restored original HOME. Confirm with the physical remote before uninstalling anything."
        )
        return
    activate(tv, state_path, args.launcher_package, args.bridge, args.persistent)
    print(
        "HOME resolves to the target; rollback record saved locally. A system request acknowledgement is not proof of persistence. Verify helper logs, physical HOME, restart and wake, as described in docs/INSTALL.md."
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
