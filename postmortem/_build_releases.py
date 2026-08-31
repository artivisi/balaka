#!/usr/bin/env python3
"""Release cadence and containment: tag timeline, commits and fix share per
release window, patch releases per minor line, time-to-first-patch.

Tag convention: YYYY.MM-RELEASE = minor line, YYYY.MM.N-RELEASE = patch.

Run from repo root:  python3 postmortem/_build_releases.py
Writes postmortem/data/releases.json and postmortem/csv/{releases,containment}.csv
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

MINOR_RE = re.compile(r"^(\d{4}\.\d{2})-RELEASE$")
PATCH_RE = re.compile(r"^(\d{4}\.\d{2})\.(\d+)-RELEASE$")


def sh(*args: str) -> str:
    r = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"{args}: {r.stderr}")
    return r.stdout


def tag_date(tag: str) -> dt.date:
    out = sh("git", "log", "-1", "--format=%ad", "--date=short", tag).strip()
    return dt.date.fromisoformat(out)


def commits_between(a: str, b: str):
    """Commit subjects in range a..b (a exclusive). a may be None."""
    rng = f"{a}..{b}" if a else b
    out = sh("git", "log", "--no-merges", "--format=%s", rng)
    return [s for s in out.splitlines() if s]


def main():
    tags = [t for t in sh("git", "tag").splitlines() if t]
    parsed = []
    for t in tags:
        m = MINOR_RE.match(t)
        p = PATCH_RE.match(t)
        if m:
            parsed.append({"tag": t, "line": m.group(1), "patch": 0})
        elif p:
            parsed.append({"tag": t, "line": p.group(1), "patch": int(p.group(2))})
        else:
            raise RuntimeError(f"tag does not match release convention: {t}")
    for r in parsed:
        r["date"] = tag_date(r["tag"])
    parsed.sort(key=lambda r: (r["date"], r["patch"]))

    rows = []
    prev_tag = None
    for r in parsed:
        subjects = commits_between(prev_tag, r["tag"])
        fixes = sum(1 for s in subjects if s.startswith("fix"))
        rows.append(
            {
                "tag": r["tag"],
                "date": r["date"].isoformat(),
                "kind": "patch" if r["patch"] else "minor",
                "line": r["line"],
                "commits": len(subjects),
                "fix_commits": fixes,
                "days_since_prev_tag": (
                    (r["date"] - parsed[parsed.index(r) - 1]["date"]).days
                    if parsed.index(r) > 0
                    else ""
                ),
            }
        )
        prev_tag = r["tag"]

    with open(OUT_CSV / "releases.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # containment per minor line
    lines = {}
    for r in parsed:
        lines.setdefault(r["line"], []).append(r)
    cont = []
    for line in sorted(lines):
        rels = lines[line]
        rels = sorted(rels, key=lambda r: r["patch"])
        # base = the minor tag, or — where a line was first tagged as .1
        # directly (2026.03) — the lowest patch number; the rest are patches
        base_rel, patches = rels[0], rels[1:]
        base = base_rel["date"]
        cont.append(
            {
                "line": line,
                "base_tag": base_rel["tag"],
                "minor_date": base.isoformat(),
                "patch_count": len(patches),
                "days_to_first_patch": (
                    (patches[0]["date"] - base).days if patches else ""
                ),
                "days_to_last_patch": (
                    (patches[-1]["date"] - base).days if patches else ""
                ),
                "patch_fix_commits": sum(
                    row["fix_commits"]
                    for row in rows
                    if row["tag"] in {p["tag"] for p in patches}
                ),
            }
        )
    with open(OUT_CSV / "containment.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(cont[0].keys()))
        w.writeheader()
        w.writerows(cont)

    summary = {
        "releases_total": len(parsed),
        "minor_lines": len(lines),
        "patch_releases": sum(1 for r in parsed if r["patch"]),
        "first_release": parsed[0]["tag"],
        "last_release": parsed[-1]["tag"],
        "containment": cont,
    }
    (OUT_DATA / "releases.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
