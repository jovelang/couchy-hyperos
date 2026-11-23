# SPDX-License-Identifier: GPL-3.0-only
"""Exercise setup/recovery order without connecting to a real television."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("tv_setup", Path(__file__).parents[1] / "tv_setup.py")
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)
STOCK = "com.mitv.tvhome/.MainActivityUserMode"


class Device:
    serial = "fixture-tv"

    def __init__(self):
        self.calls = []
        self.current = STOCK
        self.sdk = "34"
        self.libraries = "library:com.xiaomi.mitv.api\nlibrary:mitvmiddlewareimpl"
        self.policy_enabled = False
        self.accept = True
        self.endpoint = True
        self.verify = True
        self.state_path = None

    def shell(self, *args):
        self.calls.append(args)
        if args == ("am", "get-current-user"):
            return "0"
        if args == ("getprop", "ro.build.version.sdk"):
            return self.sdk
        if args == ("pm", "list", "libraries"):
            return self.libraries
        if args == ("pm", "list", "packages", "-d"):
            return ""
        return "Success"

    def installed(self, package):
        return True

    def home(self):
        return self.current

    def policy(self, method):
        self.calls.append(("policy", method))
        if not self.endpoint:
            raise RuntimeError("No endpoint")
        if method == "enable":
            # The recovery intent must be durable before any system request.
            state = json.loads(self.state_path.read_text())
            if state["home"] != STOCK or not state["persistent_requested"]:
                raise AssertionError("Recovery record not saved first")
            self.policy_enabled = True
        elif method == "disable":
            self.policy_enabled = False
        return f"Bundle[{{enabled={str(self.policy_enabled).lower()}, lastCommandAccepted={str(self.accept).lower()}}}]"

    def wait_home(self, target):
        self.calls.append(("wait", target))
        if not self.verify:
            raise RuntimeError("HOME still resolves elsewhere")


class SetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "device.json"
        self.tv = Device()
        self.tv.state_path = self.path

    def activate(self):
        setup.activate(self.tv, self.path, setup.PACKAGE, True, True)

    def state(self):
        return {
            "serial": self.tv.serial,
            "home": STOCK,
            "stock_package": "com.mitv.tvhome",
            "stock_disabled": False,
            "persistent_requested": True,
        }

    def test_native_setup_saves_baseline_and_keeps_stock_available(self):
        self.activate()
        state = json.loads(self.path.read_text())
        self.assertEqual(state["home"], STOCK)
        self.assertEqual(self.path.stat().st_mode & 0o777, 0o600)
        self.assertIn(("pm", "enable", "--user", "0", "com.mitv.tvhome"), self.tv.calls)
        self.assertFalse(
            any("disable-user" in call or "set-home-activity" in call for call in self.tv.calls)
        )
        self.assertEqual(self.tv.calls[-1], ("wait", setup.BRIDGE))

    def test_existing_baseline_is_not_replaced_by_current_bridge(self):
        self.path.write_text(json.dumps(self.state()))
        self.tv.current = setup.BRIDGE
        self.activate()
        self.assertEqual(json.loads(self.path.read_text())["home"], STOCK)

    def test_unsupported_android_leaves_home_and_files_untouched(self):
        self.tv.sdk = "33"
        with self.assertRaises(RuntimeError):
            self.activate()
        self.assertFalse(self.path.exists())
        self.assertFalse(any(call[0] == "policy" or "start" in call for call in self.tv.calls))

    def test_missing_vendor_library_does_not_enable_policy(self):
        self.tv.libraries = "library:android.test.runner"
        with self.assertRaises(RuntimeError):
            self.activate()
        self.assertFalse(self.path.exists())
        self.assertFalse(self.tv.policy_enabled)

    def test_resolver_cannot_be_saved_as_original_home(self):
        self.tv.current = "android/com.android.internal.app.ResolverActivity"
        with self.assertRaises(RuntimeError):
            self.activate()
        self.assertFalse(self.path.exists())
        self.assertNotIn(("policy", "enable"), self.tv.calls)

    def test_declined_request_retains_recovery_intent(self):
        self.tv.accept = False
        with self.assertRaises(RuntimeError):
            self.activate()
        self.assertTrue(json.loads(self.path.read_text())["persistent_requested"])
        self.assertFalse(
            any("set-home-activity" in call or "disable-user" in call for call in self.tv.calls)
        )

    def test_restore_removes_policy_before_switching_home(self):
        self.tv.policy_enabled = True
        setup.restore(self.tv, self.state())
        remove = self.tv.calls.index(("policy", "disable"))
        switch = self.tv.calls.index(("cmd", "package", "set-home-activity", "--user", "0", STOCK))
        self.assertLess(remove, switch)
        self.assertEqual(
            self.tv.calls[-2:], [("wait", STOCK), ("input", "keyevent", "KEYCODE_HOME")]
        )

    def test_unavailable_removal_stops_recovery_before_home_command(self):
        self.tv.endpoint = False
        with self.assertRaises(RuntimeError):
            setup.restore(self.tv, self.state())
        self.assertFalse(
            any("set-home-activity" in call or "keyevent" in call for call in self.tv.calls)
        )

    def test_failed_removal_ack_does_not_report_recovery(self):
        self.tv.accept = False
        with self.assertRaises(RuntimeError):
            setup.restore(self.tv, self.state())
        self.assertFalse(any("set-home-activity" in call for call in self.tv.calls))

    def test_failed_home_verification_does_not_send_home_key(self):
        self.tv.verify = False
        with self.assertRaises(RuntimeError):
            setup.restore(self.tv, self.state())
        self.assertFalse(any("keyevent" in call for call in self.tv.calls))

    def test_old_nonpersistent_record_can_restore_without_new_endpoint(self):
        self.tv.endpoint = False
        state = self.state()
        del state["persistent_requested"]
        setup.restore(self.tv, state)
        self.assertEqual(self.tv.calls[-1], ("input", "keyevent", "KEYCODE_HOME"))

    def test_other_device_or_invalid_record_cannot_change_home(self):
        for patch in [
            {"serial": "other-tv"},
            {"stock_package": "package;invalid"},
            {"home": "invalid"},
        ]:
            with self.subTest(patch=patch):
                with self.assertRaises(RuntimeError):
                    setup.restore(self.tv, dict(self.state(), **patch))
                self.assertEqual(self.tv.calls, [])


if __name__ == "__main__":
    unittest.main()
