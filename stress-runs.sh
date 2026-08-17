#!/usr/bin/env bash
#
# Run the full suite back to back N times and compare the runs.
#
#   ./stress-runs.sh 3
#
# Purpose is not the test results — run-tests.sh already reports those — but
# whether Apple Container and socktainer degrade cumulatively. A single clean
# run tells you nothing about that; the 2026-08-16 incident followed hours of
# repeated container churn, and five deliberate single-cycle reproductions
# afterwards all failed to trigger it.
#
# Between runs it records, with the engine idle and no containers alive:
#
#   - wired memory, to see whether VM memory is actually returned. Idle wired
#     climbing run over run is the leak, visible before it becomes fatal.
#   - `container list` latency, to catch the engine slowing before it wedges.
#   - leftover containers, i.e. whether Ryuk and the engine really reaped.
#
# Between-run state goes to logs/stress-<timestamp>/baselines.tsv; each run
# keeps its own logs/test-run-*/ directory.

set -uo pipefail
cd "$(dirname "$0")" || exit 1

RUNS="${1:-3}"
case "$RUNS" in
    ''|*[!0-9]*) echo "usage: $0 <number-of-runs>" >&2; exit 1 ;;
esac

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="logs/stress-${STAMP}"
BASE="${OUT}/baselines.tsv"
mkdir -p "$OUT"

wired_gb() {
    vm_stat 2>/dev/null | awk '/Pages wired down/ {gsub(/\./,"",$4); printf "%.2f", $4*16384/1073741824}'
}

# Idle reading: no containers, engine quiet. This is the number that must not
# drift upward across runs.
baseline() {
    local phase="$1" run="$2"
    local before after rc secs count
    before=$(date +%s)
    container list >/dev/null 2>&1; rc=$?
    after=$(date +%s); secs=$((after - before))
    count=$(docker ps -q 2>/dev/null | wc -l | tr -d ' ')
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$(date '+%H:%M:%S')" "$run" "$phase" "$(wired_gb)" \
        "$(sysctl -n vm.swapusage 2>/dev/null | awk '{gsub(/M/,"",$6); print $6}')" \
        "$count" "rc=${rc}/${secs}s" >> "$BASE"
}

printf 'time\trun\tphase\twiredGB\tswapMB\tleftoverContainers\tengine\n' > "$BASE"

echo "stress campaign: ${RUNS} full runs -> ${OUT}/"
echo "watch: idle wired must return to the same value between runs."
echo

for i in $(seq 1 "$RUNS"); do
    echo "───── run ${i}/${RUNS} starting $(date '+%H:%M:%S') ─────"

    # Reap anything the previous run left, so the reading is genuinely idle.
    docker ps -aq 2>/dev/null | xargs -r docker rm -f >/dev/null 2>&1
    sleep 10
    baseline "before" "$i"

    ./run-tests.sh
    rc=$?

    docker ps -aq 2>/dev/null | xargs -r docker rm -f >/dev/null 2>&1
    sleep 15
    baseline "after" "$i"

    echo "run ${i} finished rc=${rc} at $(date '+%H:%M:%S')"

    # A wedged engine will not improve by starting another 90-minute run on top
    # of it, and the evidence is most useful undisturbed.
    if ! container list >/dev/null 2>&1; then
        echo "!! ENGINE UNRESPONSIVE after run ${i} — stopping campaign."
        echo "!! This is the condition we have been trying to reproduce."
        echo "!! Capture before touching anything:"
        echo "!!   vm_stat; container list; pmset -g log | grep -E 'Sleep|Wake'"
        break
    fi
    echo
done

echo
echo "════════ campaign summary ════════"
column -t -s "$(printf '\t')" "$BASE" 2>/dev/null || cat "$BASE"
echo
awk -F'\t' 'NR>1 && $3=="before" {if (first=="") first=$4; last=$4}
    END {
        if (first != "" && last != "") {
            d = last - first
            printf "idle wired drift across campaign: %.2f -> %.2f GB (%+.2f)\n", first, last, d
            if (d > 1.0) print "LEAK SUSPECTED: idle wired grew more than 1 GB with no containers running."
            else print "no cumulative leak: idle wired returned to roughly its starting value."
        }
    }' "$BASE"
echo "logs: ${OUT}/  (per-run detail in logs/test-run-*/)"
