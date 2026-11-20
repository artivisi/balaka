#!/usr/bin/env bash
#
# Pre-pull every container image the test suite starts.
#
# Was required with Apple Container + socktainer (until 2026-11-15): docker-java could not
# parse socktainer's pull-progress stream (socktainer/socktainer#359). On OrbStack, Docker
# Engine and CI it only warms the image cache.
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
    echo "ERROR: docker CLI not found. Install Docker Engine, or OrbStack on macOS." >&2
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "ERROR: no reachable Docker API endpoint." >&2
    echo "Current context: $(docker context show 2>/dev/null || echo unknown)" >&2
    echo "On macOS: 'orb start' (OrbStack)." >&2
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
