#!/usr/bin/env python3
"""GitHub issue ledger: class (defect / feature / ops), cycle time.

Small n by design — most defects on this project were fixed commit-first
without a ticket; the issue tracker is used for externally-reported or
deferred work. Run from repo root (needs gh auth):
  python3 postmortem/_build_issues.py
Writes postmortem/data/issues.json and postmortem/csv/issues.csv
"""
import csv
import datetime as dt
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DATA = ROOT / "postmortem" / "data"
OUT_CSV = ROOT / "postmortem" / "csv"


def classify(title: str) -> str:
    t = title.lower()
    if t.startswith("bug") or "bug:" in t or re.match(r"^bug-\d+", t):
        return "defect"
    if "no test coverage" in t or "never verify" in t or "invalid json" in t \
            or "failed silently" in t or "outage" in t:
        return "defect"
    if t.startswith("api:") or t.startswith("add ") or t.startswith("feature"):
        return "feature"
    return "other"


def main():
    r = subprocess.run(
        ["gh", "issue", "list", "--state", "all", "--limit", "200", "--json",
         "number,title,state,createdAt,closedAt,labels"],
        cwd=ROOT, capture_output=True, text=True,
    )
    if r.returncode != 0:
        raise RuntimeError(r.stderr)
    issues = json.loads(r.stdout)
    rows = []
    for i in sorted(issues, key=lambda x: x["number"]):
        created = dt.datetime.fromisoformat(i["createdAt"].replace("Z", "+00:00"))
        closed = (
            dt.datetime.fromisoformat(i["closedAt"].replace("Z", "+00:00"))
            if i["closedAt"]
            else None
        )
        rows.append(
            {
                "number": i["number"],
                "state": i["state"],
                "class": classify(i["title"]),
                "created": created.date().isoformat(),
                "closed": closed.date().isoformat() if closed else "",
                "cycle_days": (closed - created).days if closed else "",
                "title": i["title"],
            }
        )
    with open(OUT_CSV / "issues.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    closed_cycles = sorted(
        r["cycle_days"] for r in rows if r["cycle_days"] != ""
    )
    summary = {
        "issues_total": len(rows),
        "open": sum(1 for r in rows if r["state"] == "OPEN"),
        "by_class": {
            c: sum(1 for r in rows if r["class"] == c)
            for c in ("defect", "feature", "other")
        },
        "cycle_days_median": (
            closed_cycles[len(closed_cycles) // 2] if closed_cycles else None
        ),
        "cycle_days_max": closed_cycles[-1] if closed_cycles else None,
    }
    (OUT_DATA / "issues.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
