"""No hardware/network access: verify auth boundaries, configuration and command dispatch."""

import asyncio
import importlib
import io
import json
import os
import shlex
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
from configuration import load_config, new_config

TEMP = tempfile.TemporaryDirectory()
os.environ["COUCHY_CONFIG"] = str(Path(TEMP.name) / "config.json")
os.environ["COUCHY_DATA_DIR"] = str(Path(TEMP.name) / "data")
CONFIG = new_config("emulator-5554", "http://127.0.0.1:8768")
Path(os.environ["COUCHY_CONFIG"]).write_text(json.dumps(CONFIG))
web = importlib.import_module("server")
from core import ControlError, TVControl
from fastapi.testclient import TestClient
from PIL import Image


async def idle_poll():
    await asyncio.Event().wait()


class ConfigurationTests(unittest.TestCase):
    def test_fresh_credentials_and_no_personal_defaults(self):
        other = new_config("emulator-5556", "http://tv-control.local:8768")
        self.assertNotEqual(CONFIG["secret"], other["secret"])
        self.assertRegex(other["pair_code"], r"^[0-9]{8}$")
        self.assertEqual(other["ha"], {})
        self.assertEqual(other["listen_host"], "127.0.0.1")
        self.assertEqual(load_config()["adb_serial"], "emulator-5554")

    def test_wildcard_host_rejected(self):
        value = dict(CONFIG, allowed_hosts=["*"])
        path = Path(TEMP.name) / "bad.json"
        path.write_text(json.dumps(value))
        with self.assertRaises(ValueError):
            load_config(path)

    def test_credentials_in_advertised_url_rejected(self):
        with self.assertRaises(ValueError):
            new_config("emulator-5554", "http://user:example@tv-control.local:8768")


class WebBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.poll = patch.object(web.control, "poll", idle_poll)
        self.poll.start()
        self.client = TestClient(
            web.app, base_url="http://127.0.0.1:8768", client=("127.0.0.1", 5000)
        )
        self.client.__enter__()
        self.origin = {"Origin": "http://127.0.0.1:8768"}

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.poll.stop()

    def login(self):
        response = self.client.post(
            "/api/login", json={"code": CONFIG["pair_code"]}, headers=self.origin
        )
        self.assertEqual(response.status_code, 200)

    def test_health_and_unauthenticated_access(self):
        self.assertEqual(self.client.get("/health").status_code, 200)
        self.assertEqual(self.client.get("/api/status").status_code, 401)
        self.assertEqual(
            self.client.post(
                "/api/install",
                files={"file": ("demo.apk", b"invalid")},
                headers=self.origin,
            ).status_code,
            401,
        )

    def test_cookie_origin_and_logout(self):
        self.login()
        self.assertEqual(self.client.get("/api/status").status_code, 200)
        self.assertEqual(
            self.client.post(
                "/api/key",
                json={"key": "home"},
                headers={"Origin": "https://example.org"},
            ).status_code,
            403,
        )
        self.assertEqual(
            self.client.post("/api/logout", json={}, headers=self.origin).status_code,
            200,
        )
        self.assertEqual(self.client.get("/api/status").status_code, 401)

    def test_bad_host_and_input_types(self):
        self.assertEqual(
            self.client.get("/health", headers={"Host": "example.org"}).status_code, 400
        )
        self.login()
        self.assertEqual(
            self.client.post("/api/key", json={"key": {}}, headers=self.origin).status_code,
            400,
        )
        self.assertEqual(
            self.client.post("/api/power", json={"on": "yes"}, headers=self.origin).status_code,
            400,
        )
        self.assertEqual(
            self.client.post("/api/login", json=[], headers=self.origin).status_code,
            400,
        )

    def test_config_whitelist_and_pairing_code_redaction(self):
        self.login()
        with patch.object(
            web.control,
            "get_config",
            AsyncMock(return_value={"nickname": "Example", "remoteControlCode": "private"}),
        ):
            response = self.client.get("/api/config")
            self.assertEqual(response.status_code, 200)
            self.assertNotIn("remoteControlCode", response.json())
        with patch.object(web.control, "patch_config", AsyncMock()) as mutate:
            self.assertEqual(
                self.client.patch(
                    "/api/config",
                    json={"remoteControlCode": "example"},
                    headers=self.origin,
                ).status_code,
                400,
            )
            self.assertEqual(
                self.client.patch(
                    "/api/config", json={"showWeather": "yes"}, headers=self.origin
                ).status_code,
                400,
            )
            mutate.assert_not_awaited()

    def test_nonshared_file_path_rejected_before_bridge(self):
        self.login()
        with patch.object(web.control, "bridge", AsyncMock()) as bridge:
            self.assertEqual(
                self.client.get("/api/files", params={"path": "/data/private"}).status_code,
                400,
            )
            bridge.assert_not_awaited()


class DispatchTests(unittest.IsolatedAsyncioTestCase):
    async def test_shell_preserves_literal_filename(self):
        control = TVControl(CONFIG)
        name = "/sdcard/Download/example $(literal).txt"
        with patch.object(control, "adb", AsyncMock(return_value="ok")) as adb:
            await control.shell("sha256sum", name)
            command = adb.await_args.args
            self.assertEqual(command[0], "shell")
            self.assertEqual(shlex.split(command[1]), ["sha256sum", name])

    async def test_custom_package_and_serial_are_used(self):
        control = TVControl(
            dict(
                CONFIG,
                launcher_package="org.example.launcher",
                adb_serial="emulator-5556",
            )
        )
        with patch("core.process", AsyncMock(return_value="")) as process:
            await control.provider("patch", {"nickname": "Example"})
            self.assertIn("emulator-5556", process.await_args.args[0])
            self.assertIn("content://org.example.launcher.config", process.await_args.args[0][-1])

    async def test_original_png_and_scaled_preview(self):
        control = TVControl(CONFIG)
        control.status["state"] = "on"
        buffer = io.BytesIO()
        Image.new("RGB", (1920, 1080), "blue").save(buffer, "PNG")
        with patch.object(control, "adb", AsyncMock(return_value=buffer.getvalue())):
            original, kind, _ = await control.screenshot(True)
            self.assertEqual(kind, "image/png")
            self.assertEqual(original, buffer.getvalue())
            preview, kind, _ = await control.screenshot(False)
            self.assertEqual(kind, "image/jpeg")
            self.assertEqual(Image.open(io.BytesIO(preview)).size, (1280, 720))

    async def test_unconfigured_power_does_not_send_commands(self):
        control = TVControl(CONFIG)
        control.status["state"] = "standby"
        with (
            patch.object(control, "refresh_status", AsyncMock()),
            patch.object(control, "ha_action", AsyncMock()) as action,
        ):
            with self.assertRaises(ControlError):
                await control.power(True, {})
            action.assert_not_awaited()


if __name__ == "__main__":
    unittest.main()
