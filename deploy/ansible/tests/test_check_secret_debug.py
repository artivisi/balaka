#!/usr/bin/env python3
"""Regression tests for check-secret-debug.py.

Run with:
    python3 -m unittest discover -s deploy/ansible/tests -p 'test_*.py'

Fixtures are synthetic; no real credentials are used.
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path

CHECKER = Path(__file__).with_name("check-secret-debug.py")


def load_checker():
    spec = importlib.util.spec_from_file_location("check_secret_debug", CHECKER)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class ScanTaskTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.checker = load_checker()

    def scan(self, debug):
        _, found = self.checker.scan_task({"name": "test debug", "debug": debug})
        return found

    def test_msg_interpolation_is_found(self):
        found = self.scan({"msg": "password is {{ admin_password_plain }}"})
        self.assertEqual(found, {"admin_password_plain"})

    def test_direct_secret_var_is_found(self):
        found = self.scan({"var": "admin_password_plain"})
        self.assertEqual(found, {"admin_password_plain"})

    def test_jinja_secret_var_is_found(self):
        found = self.scan({"var": "{{ admin_password_plain }}"})
        self.assertEqual(found, {"admin_password_plain"})

    def test_jinja_suffix_secret_var_is_found(self):
        found = self.scan({"var": "{{ backup_gdrive_token }}"})
        self.assertEqual(found, {"backup_gdrive_token"})

    def test_non_secret_var_is_ignored(self):
        found = self.scan({"var": "{{ app_domain }}"})
        self.assertEqual(found, set())


class MainTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.checker = load_checker()

    def run_checker(self, yaml_text: str) -> int:
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "playbook.yml"
            fixture.write_text(yaml_text, encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(
                io.StringIO()
            ):
                return self.checker.main(["check-secret-debug.py", str(fixture)])

    def test_jinja_var_fixture_exits_1(self):
        rc = self.run_checker(
            "---\n"
            "- name: leak\n"
            "  ansible.builtin.debug:\n"
            '    var: "{{ admin_password_plain }}"\n'
        )
        self.assertEqual(rc, 1)

    def test_clean_fixture_exits_0(self):
        rc = self.run_checker(
            "---\n"
            "- name: safe\n"
            "  ansible.builtin.debug:\n"
            '    msg: "deployed {{ app_domain }}"\n'
        )
        self.assertEqual(rc, 0)


if __name__ == "__main__":
    unittest.main()
