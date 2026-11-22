#!/usr/bin/env python3
"""Show this installation's address and connection code on the paired launcher."""

# SPDX-License-Identifier: GPL-3.0-only
import argparse
import json
import os
import shlex
import subprocess
from configuration import DEFAULT_PACKAGE, load_config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--show-code",
        action="store_true",
        help="Also print the private connection code locally",
    )
    args = parser.parse_args()
    config = load_config()
    package = config.get("launcher_package", DEFAULT_PACKAGE)
    patch = {
        "remoteControlUrl": config["url"],
        "remoteControlLanUrl": config["lan_url"],
        "remoteControlCode": config["pair_code"],
    }
    command = [
        "content",
        "call",
        "--uri",
        f"content://{package}.config",
        "--method",
        "patch",
        "--arg",
        json.dumps(patch, ensure_ascii=False),
    ]
    result = subprocess.run(
        [
            os.environ.get("ADB", "adb"),
            "-s",
            config["adb_serial"],
            "shell",
            shlex.join(command),
        ],
        capture_output=True,
        text=True,
        timeout=30,
    )
    if result.returncode or "updated=true" not in result.stdout:
        raise SystemExit(
            "Could not provision the TV. Install the matching launcher, open it once, and authorize this ADB host."
        )
    print("Ready. Open Desktop settings → Remote control on the TV.")
    if args.show_code:
        print("Connection code:", config["pair_code"])


if __name__ == "__main__":
    main()
