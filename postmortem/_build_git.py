#!/usr/bin/env python3
"""Git-history measures: commit taxonomy, monthly maintenance mix, module
fix density, hot files, active days, hour-of-day distribution, LOC.

Run from repo root:  python3 postmortem/_build_git.py
Writes postmortem/data/git.json and postmortem/csv/{commits,monthly_mix,
fix_density,hot_files}.csv
"""
import csv
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DATA = ROOT / "postmortem" / "data"
OUT_CSV = ROOT / "postmortem" / "csv"

TYPE_RE = re.compile(r"^([a-z]+)(\([^)]*\))?!?:\s")
KNOWN_TYPES = {
    "feat", "fix", "docs", "refactor", "test", "chore", "release",
    "ci", "build", "style", "perf",
}


def sh(*args: str) -> str:
    r = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"{args}: {r.stderr}")
    return r.stdout


def classify_type(subject: str) -> str:
    m = TYPE_RE.match(subject)
    if m and m.group(1) in KNOWN_TYPES:
        return m.group(1)
    return "other"


def classify_path(path: str) -> str:
    if path.startswith("src/main/java"):
        return "main-java"
    if path.startswith("src/test"):
        return "test"
    if path.startswith("src/main/resources/templates"):
        return "templates"
    if path.startswith("src/main/resources/db"):
        return "migrations"
    if path.startswith("src/main/frontend") or path.startswith(
        "src/main/resources/static"
    ):
        return "frontend"
    if path.startswith("src/main/resources"):
        return "resources"
    if path.startswith("deploy/"):
        return "deploy"
    if path.startswith("docs/"):
        return "docs"
    if path.startswith(".github"):
        return "ci"
    if path.startswith("industry-seed/"):
        return "seed-data"
    return "build-other"


def collect_commits():
    """Parse git log with numstat. Returns list of commit dicts."""
    raw = sh(
        "git", "log", "--no-merges", "--numstat",
        "--format=@@@%H|%ad|%an|%s", "--date=format:%Y-%m-%d %H",
    )
    commits = []
    cur = None
    for line in raw.splitlines():
        if line.startswith("@@@"):
            h, ad, an, subj = line[3:].split("|", 3)
            date, hour = ad.split(" ")
            cur = {
                "hash": h,
                "date": date,
                "hour": int(hour),
                "author": an,
                "subject": subj,
                "type": classify_type(subj),
                "areas": Counter(),
                "files": [],
            }
            commits.append(cur)
        elif line.strip() and cur is not None:
            parts = line.split("\t")
            if len(parts) == 3:
                add, dele, path = parts
                churn = (0 if add == "-" else int(add)) + (
                    0 if dele == "-" else int(dele)
                )
                cur["areas"][classify_path(path)] += churn
                cur["files"].append(path)
    return commits


def dominant_area(c) -> str:
    if not c["areas"]:
        return "none"
    return c["areas"].most_common(1)[0][0]


def loc_by_area():
    """Non-blank LOC of the current working tree, tracked files only."""
    files = sh("git", "ls-files").splitlines()
    exts = {".java", ".html", ".sql", ".js", ".css", ".ts", ".py",
            ".yml", ".yaml", ".xml", ".sh", ".properties", ".md", ".csv"}
    loc = Counter()
    fcount = Counter()
    for f in files:
        p = ROOT / f
        if p.suffix not in exts or not p.is_file():
            continue
        area = classify_path(f)
        try:
            n = sum(
                1
                for ln in p.read_text(errors="replace").splitlines()
                if ln.strip()
            )
        except OSError as e:
            raise RuntimeError(f"unreadable tracked file {f}") from e
        loc[area] += n
        fcount[area] += 1
    return loc, fcount


def main():
    commits = collect_commits()
    OUT_DATA.mkdir(parents=True, exist_ok=True)
    OUT_CSV.mkdir(parents=True, exist_ok=True)

    with open(OUT_CSV / "commits.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["hash", "date", "hour", "author", "type", "area", "subject"])
        for c in commits:
            w.writerow(
                [c["hash"][:9], c["date"], c["hour"], c["author"],
                 c["type"], dominant_area(c), c["subject"]]
            )

    # monthly maintenance mix
    monthly = defaultdict(Counter)
    for c in commits:
        monthly[c["date"][:7]][c["type"]] += 1
    types = sorted({t for m in monthly.values() for t in m})
    with open(OUT_CSV / "monthly_mix.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["month", "total"] + types)
        for month in sorted(monthly):
            row = monthly[month]
            w.writerow([month, sum(row.values())] + [row.get(t, 0) for t in types])

    # fix density per area: fix commits whose dominant area is X / current KLOC of X
    loc, fcount = loc_by_area()
    fix_by_area = Counter(
        dominant_area(c) for c in commits if c["type"] == "fix"
    )
    all_by_area = Counter(dominant_area(c) for c in commits)
    with open(OUT_CSV / "fix_density.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(
            ["area", "files", "loc_nonblank", "commits", "fix_commits",
             "fix_per_kloc"]
        )
        for area in sorted(loc, key=lambda a: -loc[a]):
            fixes = fix_by_area.get(area, 0)
            dens = round(fixes / loc[area] * 1000, 1) if loc[area] else ""
            w.writerow(
                [area, fcount[area], loc[area], all_by_area.get(area, 0),
                 fixes, dens]
            )

    # hot files: most-touched by fix commits
    fix_touch = Counter()
    for c in commits:
        if c["type"] == "fix":
            for f in set(c["files"]):
                fix_touch[f] += 1
    with open(OUT_CSV / "hot_files.csv", "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "fix_commits_touching"])
        for f, n in fix_touch.most_common(40):
            w.writerow([f, n])

    hours = Counter(c["hour"] for c in commits)
    days = sorted({c["date"] for c in commits})
    weekday = Counter()
    import datetime as dt
    for d in days:
        weekday[dt.date.fromisoformat(d).strftime("%a")] += 1

    summary = {
        "commits_total": len(commits),
        "first_commit": commits[-1]["date"],
        "last_commit": commits[0]["date"],
        "authors": dict(Counter(c["author"] for c in commits)),
        "type_counts": dict(Counter(c["type"] for c in commits).most_common()),
        "active_days": len(days),
        "calendar_days": (
            dt.date.fromisoformat(commits[0]["date"])
            - dt.date.fromisoformat(commits[-1]["date"])
        ).days + 1,
        "active_days_by_weekday": dict(weekday),
        "commits_by_hour": {str(h): hours.get(h, 0) for h in range(24)},
        "loc_nonblank_by_area": dict(loc.most_common()),
        "files_by_area": dict(fcount.most_common()),
        "coauthored_claude": sum(
            1 for c in commits if "Claude" in c["author"]
        ),
    }
    (OUT_DATA / "git.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
