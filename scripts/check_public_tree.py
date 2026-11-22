#!/usr/bin/env python3
"""Audit publishable files; ignored runtime directories are never part of a release."""

# SPDX-License-Identifier: GPL-3.0-only
import argparse
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {
    ".git",
    ".gradle",
    ".kotlin",
    "build",
    ".idea",
    "__pycache__",
    ".venv",
    ".ruff_cache",
    ".local",
    "artifacts",
}


def runtime_path(relative):
    return any(part in EXCLUDED for part in relative.parts) or relative.parts[:2] in {
        ("server", "data"),
        ("server", "logs"),
    }


FORBIDDEN_SUFFIXES = {
    ".apk",
    ".aab",
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".idsig",
    ".log",
}


def files():
    result = subprocess.run(["git", "-C", str(ROOT), "ls-files", "-z"], capture_output=True)
    if result.returncode == 0 and result.stdout:
        return [ROOT / name.decode() for name in result.stdout.split(b"\0") if name]
    return [p for p in ROOT.rglob("*") if p.is_file() and not runtime_path(p.relative_to(ROOT))]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--forbid-value",
        action="append",
        default=[],
        help="Additional locally supplied sensitive strings; values are never printed",
    )
    args = parser.parse_args()
    problems = []
    for path in files():
        rel = path.relative_to(ROOT)
        if runtime_path(rel):
            problems.append((rel, "runtime/build file is tracked"))
            continue
        if path.is_symlink():
            problems.append((rel, "symlink needs review"))
            continue
        if (
            path.name in {"config.json", "local.properties", ".env"}
            or path.suffix in FORBIDDEN_SUFFIXES
        ):
            problems.append((rel, "local configuration or binary artifact"))
            continue
        data = path.read_bytes()
        for value in args.forbid_value:
            if value and value.encode() in data:
                problems.append((rel, "locally forbidden value"))
        key_marker = rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?" + rb"PRIVATE KEY-----"
        if re.search(key_marker, data):
            problems.append((rel, "private key marker"))
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            if str(rel) != "gradle/wrapper/gradle-wrapper.jar":
                problems.append((rel, "unreviewed binary resource"))
            continue
        # The regex is split so its own source does not contain a matching home path.
        home = r"/" + r"(?:Users|home)/[A-Za-z0-9_.-]+/"
        if re.search(home, text):
            problems.append((rel, "absolute personal home path"))
        if re.search(r"xiaomi_" + r"cn_[0-9]{6,}", text):
            problems.append((rel, "real Mijia device identifier"))
        if re.search(r"(?:gh[pousr]_" + r"[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,})", text):
            problems.append((rel, "credential pattern"))
    if problems:
        for rel, reason in problems:
            print(f"FAIL {rel}: {reason}")
        raise SystemExit(1)
    print(f"Public tree check passed ({len(files())} files; manual review still required).")


if __name__ == "__main__":
    main()
