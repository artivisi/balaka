#!/usr/bin/env bash
#
# Instrumented wrapper for the test suite. Use this instead of a bare ./mvnw test.
#
#   ./run-tests.sh                             # full suite
#   ./run-tests.sh -Dtest=MfgBomTest           # any maven args are passed through
#
# It exists because three things went wrong on 2026-08-16 and cost a day of
# misdiagnosis:
#
#   1. The machine slept seven times mid-run. Suspending the host freezes the
#      container VMs, so Playwright navigations and awaitility waits blow their
#      timeouts. Tests then fail as TimeoutError with elapsed times matching the
#      sleep duration (one reported 5015s), which reads as flaky application
#      code rather than a suspended laptop. This wraps the run in `caffeinate`.
#
#   2. The log was written to target/, which `mvn clean` deletes — the run
#      continued writing to a removed inode and the output was unrecoverable.
#      Output goes to logs/ instead, which is gitignored and survives clean.
#
#   3. When the engine wedged (XPC timeout) and leaked ~14 GB as wired memory,
#      no state had been captured, so the bug could not be reported upstream.
#      A sampler records memory/engine health throughout and dumps forensics the
#      moment anything looks wrong.
#
# Everything lands in logs/test-run-<timestamp>/.

set -uo pipefail

cd "$(dirname "$0")" || exit 1

RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT="logs/test-run-${RUN_ID}"
METRICS="${OUT}/metrics.tsv"
TEST_LOG="${OUT}/test-output.log"
EVENTS="${OUT}/events.log"

SAMPLE_SECONDS=30
# Wired above this is not normal for this workload — baseline is ~2.3 GB.
WIRED_ALERT_GB=8
# `container list` answers in under a second when healthy.
ENGINE_SLOW_SECONDS=10

mkdir -p "$OUT"

note() { echo "$(date '+%H:%M:%S') $*" | tee -a "$EVENTS"; }

# macOS ships no coreutils `timeout`, and a wedged engine is exactly the case
# where an un-bounded call hangs the sampler forever — which is how the last
# incident went unrecorded. Bound it ourselves.
#   run_limited <seconds> <outfile> <command...>   -> rc, or 124 on timeout
run_limited() {
    local secs="$1" out="$2"; shift 2
    "$@" > "$out" 2>&1 &
    local pid=$! waited=0
    while kill -0 "$pid" 2>/dev/null; do
        if [ "$waited" -ge "$secs" ]; then
            kill -9 "$pid" 2>/dev/null
            wait "$pid" 2>/dev/null
            return 124
        fi
        sleep 1
        waited=$((waited + 1))
    done
    wait "$pid" 2>/dev/null
    return $?
}

# --- preflight -------------------------------------------------------------
# Fail loudly rather than producing a run whose results cannot be trusted.

if pgrep -f "classworlds.launcher.Launcher" >/dev/null 2>&1; then
    echo "ERROR: a maven run is already in progress." >&2
    echo "The suite must never run twice concurrently — the second run's" >&2
    echo "containers starve the first and both produce bogus timeouts." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "ERROR: no reachable Docker API endpoint." >&2
    echo "Context: $(docker context show 2>/dev/null || echo unknown)" >&2
    echo "On macOS: 'container system start', then kickstart socktainer:" >&2
    echo "  launchctl kickstart -k gui/\$(id -u)/com.socktainer.local" >&2
    exit 1
fi

CAFFEINATE=""
if [ "$(uname -s)" = "Darwin" ]; then
    if ! command -v caffeinate >/dev/null 2>&1; then
        echo "ERROR: caffeinate not found on macOS; refusing to run." >&2
        echo "A run that sleeps produces failures indistinguishable from real ones." >&2
        exit 1
    fi
    CAFFEINATE="caffeinate -is"
    note "caffeinate enabled (idle+system sleep held off)"
    note "NOTE: closing the lid still sleeps unconditionally — leave it open."
    if pmset -g batt 2>/dev/null | grep -q "Battery Power"; then
        note "WARNING: on battery. Clamshell/idle sleep is far more likely — connect AC."
    fi
fi

# --- sampler ---------------------------------------------------------------

forensics() {
    local reason="$1"
    local f="${OUT}/forensics-$(date +%H%M%S).txt"
    {
        echo "===== FORENSIC CAPTURE: ${reason} ====="
        date
        echo
        echo "--- vm_stat ---";            vm_stat 2>&1
        echo "--- swap ---";               sysctl vm.swapusage 2>&1
        local tmp="${OUT}/.forensic.tmp"
        echo "--- container list ---"
        run_limited 30 "$tmp" container list; echo "(rc=$?)"; cat "$tmp"
        echo "--- container system status ---"
        run_limited 30 "$tmp" container system status; echo "(rc=$?)"; cat "$tmp"
        echo "--- docker ps ---"
        run_limited 30 "$tmp" docker ps -a; echo "(rc=$?)"; cat "$tmp"
        rm -f "$tmp"
        echo "--- top processes by RSS ---"
        ps -axo pid,rss,pcpu,comm 2>/dev/null | sort -rnk2 | head -20
        echo "--- sleep/wake today ---"
        pmset -g log 2>/dev/null | awk '$4=="Sleep"||$4=="Wake"||$4=="DarkWake"' | grep "$(date +%Y-%m-%d)" | tail -20
        echo "--- load ---";               uptime
    } > "$f" 2>&1
    note "FORENSICS captured -> $f  (${reason})"
}

sampler() {
    printf 'time\twiredGB\tfreeGB\tcompGB\tswapMB\tcontainers\treservedMB\tengineRC\tengineSecs\tload1\n' > "$METRICS"
    local alerted_wired=0
    while true; do
        local w f c
        read -r w f c <<<"$(vm_stat 2>/dev/null | awk '
            /Pages free/ {gsub(/\./,"",$3); fr=$3}
            /Pages wired down/ {gsub(/\./,"",$4); wi=$4}
            /Pages occupied by compressor/ {gsub(/\./,"",$5); co=$5}
            END {printf "%.2f %.2f %.2f", wi*16384/1073741824, fr*16384/1073741824, co*16384/1073741824}')"

        local swap; swap=$(sysctl -n vm.swapusage 2>/dev/null | awk '{gsub(/M/,"",$6); print $6}')

        local start; start=$(date +%s)
        local lf="${OUT}/.list.tmp"
        run_limited 60 "$lf" container list; local rc=$?
        local listing; listing=$(cat "$lf" 2>/dev/null)
        local secs=$(( $(date +%s) - start ))

        local count=0 reserved=0
        if [ $rc -eq 0 ]; then
            count=$(echo "$listing" | tail -n +2 | grep -c . )
            reserved=$(echo "$listing" | awk 'NR>1 {for(i=1;i<=NF;i++) if($i=="MB") s+=$(i-1)} END {print s+0}')
        fi

        local load1; load1=$(uptime | sed 's/.*load averages*: *//' | awk '{print $1}')
        printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
            "$(date '+%H:%M:%S')" "$w" "$f" "$c" "$swap" "$count" "$reserved" "$rc" "$secs" "$load1" >> "$METRICS"

        # Anomalies worth a full dump.
        if [ $rc -ne 0 ]; then
            forensics "container list failed rc=$rc"
        elif [ "$secs" -ge "$ENGINE_SLOW_SECONDS" ]; then
            forensics "container list slow (${secs}s)"
        elif [ "$alerted_wired" -eq 0 ] && awk -v a="$w" -v b="$WIRED_ALERT_GB" 'BEGIN{exit !(a>b)}'; then
            alerted_wired=1
            forensics "wired memory ${w}GB exceeds ${WIRED_ALERT_GB}GB"
        fi

        sleep "$SAMPLE_SECONDS"
    done
}

# --- run -------------------------------------------------------------------

note "run ${RUN_ID} starting: ./mvnw test $*"
note "output -> ${OUT}/"

START_EPOCH=$(date +%s)
START_CLOCK=$(date '+%H:%M:%S')

sampler & SAMPLER_PID=$!
# shellcheck disable=SC2064
trap "kill $SAMPLER_PID 2>/dev/null; rm -f '${OUT}/.list.tmp' '${OUT}/.forensic.tmp'" EXIT INT TERM

# shellcheck disable=SC2086
$CAFFEINATE ./mvnw test "$@" > "$TEST_LOG" 2>&1
MVN_RC=$?

kill "$SAMPLER_PID" 2>/dev/null
END_CLOCK=$(date '+%H:%M:%S')
ELAPSED=$(( $(date +%s) - START_EPOCH ))

# --- verdict ---------------------------------------------------------------

note "maven exited rc=${MVN_RC} after $((ELAPSED/60))m$((ELAPSED%60))s"

# Sleep during the run invalidates timeout-shaped failures. Say so plainly,
# because this is the single most misleading failure mode on this machine.
SLEEPS=$(pmset -g log 2>/dev/null | awk -v d="$(date +%Y-%m-%d)" -v s="$START_CLOCK" -v e="$END_CLOCK" \
    '$0 ~ "^"d && $4=="Sleep" && $5!="Requests" && $2>=s && $2<=e' | wc -l | tr -d ' ')
{
    echo
    echo "=== sleep/wake during run (${START_CLOCK}-${END_CLOCK}) ==="
    pmset -g log 2>/dev/null | awk -v d="$(date +%Y-%m-%d)" -v s="$START_CLOCK" -v e="$END_CLOCK" \
        '$0 ~ "^"d && ($4=="Sleep"||$4=="Wake") && $5!="Requests" && $2>=s && $2<=e {print $2, $4}'
} >> "$EVENTS"

echo
echo "──────────────────────────────────────────────────────────"
grep -E "Tests run:.*Failures.*Errors.*Skipped:" "$TEST_LOG" | tail -1
grep -E "BUILD SUCCESS|BUILD FAILURE" "$TEST_LOG" | tail -1
awk -F'\t' 'NR>1 {if ($2+0>w) w=$2+0; if ($6+0>c) c=$6+0; if ($9+0>s) s=$9+0}
    END {printf "peak wired %.2f GB | peak containers %d | slowest engine call %ds\n", w, c, s}' "$METRICS"

if [ "$SLEEPS" -gt 0 ]; then
    echo
    echo "!! The machine slept ${SLEEPS} time(s) during this run."
    echo "!! Timeout-shaped failures are NOT trustworthy — suspending the host"
    echo "!! freezes the container VMs and blows Playwright/awaitility waits."
    echo "!! Re-run before investigating any TimeoutError. See ${EVENTS}"
fi

if ls "${OUT}"/forensics-*.txt >/dev/null 2>&1; then
    echo
    echo "!! Engine/memory anomalies were captured:"
    ls -1 "${OUT}"/forensics-*.txt | sed 's/^/     /'
    echo "!! If the engine wedged or wired memory ballooned, these are the"
    echo "!! artifacts to attach to an apple/container issue."
fi

echo "──────────────────────────────────────────────────────────"
echo "logs: ${OUT}/"
exit $MVN_RC
