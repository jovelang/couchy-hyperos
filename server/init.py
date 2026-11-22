#!/usr/bin/env python3
"""Generate fresh local credentials; never overwrite an existing installation."""

# SPDX-License-Identifier: GPL-3.0-only
import argparse
import json
import os
from configuration import config_path, new_config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--adb-serial", required=True, help="Authorized device serial from adb devices"
    )
    parser.add_argument(
        "--url", required=True, help="Address used by phones/computers, including port"
    )
    parser.add_argument("--lan-url", help="Address reachable from the TV; defaults to --url")
    parser.add_argument("--listen", default="127.0.0.1", help="Use 0.0.0.0 to accept LAN clients")
    args = parser.parse_args()
    value = new_config(args.adb_serial, args.url, args.lan_url, args.listen)
    path = config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(
        "Created local configuration. Run provision.py to display the address and code on the TV."
    )


if __name__ == "__main__":
    main()
