#!/usr/bin/env bash
#
# Pre-pull every container image the test suite starts.
#
# Required on the macOS dev machine (Apple Container + socktainer): docker-java
# cannot parse socktainer's pull-progress stream and aborts the test with
#   DockerClientException: Could not pull image: Image digest: sha256:...
# even though the image downloads fine. Pulling via the CLI first avoids it.
#
# Upstream: https://github.com/socktainer/socktainer/issues/359 — fixed on main by
# PR #365 (2026-08-16) but not in a release yet (latest v1.2.1, 2026-08-01).
# Delete this script once an installed release carries the fix.
#
# Harmless (and still useful, as a warm cache) on Docker Engine / Linux / CI.
#
# Keep RYUK/SSHD in sync with the Testcontainers version in pom.xml — they are
# started by Testcontainers itself, not by our test code.

set -euo pipefail

IMAGES=(
    "postgres:18-alpine"              # TestcontainersConfiguration
    "ghcr.io/zaproxy/zaproxy:stable"  # ZapDastTestBase
    "testcontainers/ryuk:0.14.0"      # Testcontainers resource reaper
    "testcontainers/sshd:1.3.0"       # Testcontainers.exposeHostPorts (DAST)
)

if ! command -v docker &> /dev/null; then
    echo "ERROR: docker CLI not found. Install Docker Engine, or on macOS:" >&2
    echo "  brew install container socktainer" >&2
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: no reachable Docker API endpoint." >&2
    echo "Current context: $(docker context show 2>/dev/null || echo unknown)" >&2
    echo "On macOS start both: 'container system start' and the socktainer LaunchAgent." >&2
    exit 1
fi

echo "Docker context: $(docker context show)"

failed=()
for image in "${IMAGES[@]}"; do
    echo "==> pulling ${image}"
    if ! docker pull "${image}"; then
        failed+=("${image}")
    fi
done

if [ ${#failed[@]} -gt 0 ]; then
    echo "ERROR: failed to pull ${#failed[@]} image(s):" >&2
    printf '  %s\n' "${failed[@]}" >&2
    exit 1
fi

echo "All ${#IMAGES[@]} test images present."
