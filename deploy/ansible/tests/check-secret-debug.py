#!/usr/bin/env python3
"""Fail when an Ansible ``debug`` task interpolates a secret variable.

Ansible ``debug`` output goes to stdout, to CI logs, and to any saved playbook
log. Those sinks are usually less protected than ``group_vars/all.yml`` and may
be retained by CI or support tooling, so a secret that is printed once can leak
permanently.

The check parses every YAML file under ``deploy/ansible`` (or the paths given on
the command line), finds ``debug`` / ``ansible.builtin.debug`` tasks, and looks
at the identifiers used inside each ``{{ ... }}`` expression in ``msg`` and in
``var``. Any identifier that looks like a credential is a finding.

Usage:
    ./deploy/ansible/tests/check-secret-debug.py [PATH ...]

Exit codes:
    0  no secret variable is interpolated into debug output
    1  at least one finding
    2  prerequisite missing or a YAML file could not be parsed

Limitations: this is a static check for direct variable references. It cannot
follow data through loops, lookups, ``set_fact``, or a registered variable that
was filled by ``slurp``/``command``. Keep secrets out of debug output by design;
do not rely on this check alone for indirect flows.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover - exercised on a host without PyYAML
    print(
        "PyYAML is required (it ships with ansible-core). "
        "Install ansible-core or PyYAML and retry.",
        file=sys.stderr,
    )
    raise SystemExit(2)

DEBUG_KEYS = ("debug", "ansible.builtin.debug")
JINJA_EXPRESSION = re.compile(r"{{(.*?)}}", re.DOTALL)
IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


class AnsibleSafeLoader(yaml.SafeLoader):
    """SafeLoader that tolerates Ansible tags such as ``!vault`` and ``!unsafe``."""


def _construct_unknown_tag(loader, node):
    if isinstance(node, yaml.ScalarNode):
        return loader.construct_scalar(node)
    if isinstance(node, yaml.SequenceNode):
        return loader.construct_sequence(node)
    return loader.construct_mapping(node)


AnsibleSafeLoader.add_constructor(None, _construct_unknown_tag)

# Explicit names that do not match a suffix below (admin_password_plain ends in
# _plain, and registered content has no suffix at all).
SECRET_NAMES = {
    "admin_password_plain",
    "admin_password_bcrypt",
    "backup_key_content",
    "backup_encryption_key",
    "backup_gdrive_credentials",
    "backup_gdrive_token",
    "gdrive_credentials",
    "google_cloud_credentials",
}

SECRET_SUFFIXES = (
    "_password",
    "_passwd",
    "_passphrase",
    "_secret",
    "_token",
    "_api_key",
    "_apikey",
    "_application_key",
    "_private_key",
    "_encryption_key",
    "_remember_me_key",
    "_credentials",
)


def is_secret(identifier: str) -> bool:
    name = identifier.lower()
    if name in SECRET_NAMES:
        return True
    return name.endswith(SECRET_SUFFIXES)


def walk_debug_tasks(node: object):
    """Yield every debug task mapping in a parsed YAML document."""
    if isinstance(node, dict):
        if any(key in node for key in DEBUG_KEYS):
            yield node
        for value in node.values():
            yield from walk_debug_tasks(value)
    elif isinstance(node, list):
        for value in node:
            yield from walk_debug_tasks(value)


def strings(value: object):
    """Yield all strings inside a msg value (string or list of strings)."""
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from strings(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings(item)


def referenced_secrets(text: str) -> set[str]:
    found: set[str] = set()
    for expression in JINJA_EXPRESSION.findall(text):
        for identifier in IDENTIFIER.findall(expression):
            if is_secret(identifier):
                found.add(identifier)
    return found


def scan_task(task: dict) -> tuple[str, set[str]]:
    task_name = str(task.get("name") or "<unnamed debug task>")
    debug = next(task[key] for key in DEBUG_KEYS if key in task)
    found: set[str] = set()
    if isinstance(debug, dict):
        for text in strings(debug.get("msg")):
            found |= referenced_secrets(text)
        var = debug.get("var")
        if isinstance(var, str):
            found |= referenced_secrets(var)
            if is_secret(var.strip()):
                found.add(var.strip())
    elif isinstance(debug, str):
        found |= referenced_secrets(debug)
    return task_name, found


def yaml_files(paths: list[str]) -> list[Path]:
    files: list[Path] = []
    for raw in paths:
        path = Path(raw)
        if path.is_dir():
            files.extend(sorted(path.rglob("*.yml")))
            files.extend(sorted(path.rglob("*.yaml")))
        else:
            files.append(path)
    return sorted(set(files))


def main(argv: list[str]) -> int:
    default_root = Path(__file__).resolve().parents[1]
    paths = argv[1:] or [str(default_root)]

    findings: list[str] = []
    debug_tasks = 0
    files = yaml_files(paths)
    if not files:
        print(f"no YAML files found under: {', '.join(paths)}", file=sys.stderr)
        return 2

    for path in files:
        try:
            documents = list(
                yaml.load_all(
                    path.read_text(encoding="utf-8"), Loader=AnsibleSafeLoader
                )
            )
        except (OSError, yaml.YAMLError) as exc:
            print(f"cannot parse {path}: {exc}", file=sys.stderr)
            return 2
        for document in documents:
            for task in walk_debug_tasks(document):
                debug_tasks += 1
                task_name, secrets = scan_task(task)
                for secret in sorted(secrets):
                    findings.append(
                        f"{path}: debug task '{task_name}' interpolates "
                        f"secret variable '{secret}'"
                    )

    for finding in findings:
        print(f"FAIL: {finding}")
    print(
        f"secret-debug scan: files={len(files)} debug_tasks={debug_tasks} "
        f"findings={len(findings)}"
    )
    if findings:
        print("FAILED: remove the secret from debug output or print a masked placeholder")
        return 1
    print("PASS: no secret variable is interpolated into Ansible debug output")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
