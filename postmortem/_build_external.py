#!/usr/bin/env python3
"""Pull the two public third-party quality feeds:

- SonarCloud analysis history (project artivisi_aplikasi-akunting)
- Codecov per-commit coverage (github/artivisi/balaka)

Both are public APIs, no token. Run from repo root:
  python3 postmortem/_build_external.py
Writes postmortem/data/{sonar,codecov}.json and
postmortem/csv/{sonar_history,codecov_history}.csv
"""
import csv
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DATA = ROOT / "postmortem" / "data"
OUT_CSV = ROOT / "postmortem" / "csv"

SONAR_COMPONENT = "artivisi_aplikasi-akunting"
SONAR_METRICS = [
    "ncloc",
    "bugs",
    "vulnerabilities",
    "code_smells",
    "duplicated_lines_density",
    "sqale_index",
    "security_hotspots_reviewed",
]
CODECOV_BASE = "https://api.codecov.io/api/v2/github/artivisi/repos/balaka"


def get_json(url: str):
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.load(r)


def sonar():
    url = (
        "https://sonarcloud.io/api/measures/search_history"
        f"?component={SONAR_COMPONENT}&metrics={','.join(SONAR_METRICS)}&ps=1000"
    )
    d = get_json(url)
    by_date = {}
    for m in d["measures"]:
        for h in m["history"]:
            date = h["date"][:10]
            by_date.setdefault(date, {})[m["metric"]] = h.get("value")
    rows = [{"date": k, **v} for k, v in sorted(by_date.items())]
    with open(OUT_CSV / "sonar_history.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=["date"] + SONAR_METRICS)
        w.writeheader()
        w.writerows(rows)
    (OUT_DATA / "sonar.json").write_text(json.dumps(rows, indent=2))
    return rows


def codecov():
    rows = []
    page = 1
    while True:
        d = get_json(f"{CODECOV_BASE}/commits/?page_size=100&page={page}")
        for r in d["results"]:
            t = r.get("totals")
            if t is None or t.get("coverage") is None:
                continue
            rows.append(
                {
                    "date": r["timestamp"][:10],
                    "commit": r["commitid"][:9],
                    "coverage": t["coverage"],
                    "lines": t["lines"],
                    "hits": t["hits"],
                }
            )
        if not d.get("next"):
            break
        page += 1
    rows.sort(key=lambda r: r["date"])
    with open(OUT_CSV / "codecov_history.csv", "w", newline="") as fh:
        w = csv.DictWriter(
            fh, fieldnames=["date", "commit", "coverage", "lines", "hits"]
        )
        w.writeheader()
        w.writerows(rows)
    (OUT_DATA / "codecov.json").write_text(json.dumps(rows, indent=2))
    return rows


def main():
    OUT_DATA.mkdir(parents=True, exist_ok=True)
    OUT_CSV.mkdir(parents=True, exist_ok=True)
    s = sonar()
    c = codecov()
    print(f"sonar: {len(s)} analyses {s[0]['date']} → {s[-1]['date']}")
    print(f"codecov: {len(c)} commits {c[0]['date']} → {c[-1]['date']}, "
          f"coverage {c[0]['coverage']} → {c[-1]['coverage']}")


if __name__ == "__main__":
    main()
