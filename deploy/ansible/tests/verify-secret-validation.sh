#!/usr/bin/env bash
#
# Verify the production secret pre_tasks in site.yml and deploy.yml.
#
# Runs the real playbooks against a local, non-privileged inventory with
# --tags preflight, so only the secret validation tasks execute. No server,
# no package installation, no database, and no real secrets are used.
#
# Prerequisites:
#   - ansible-playbook on PATH (or set ANSIBLE_PLAYBOOK)
#   - collections from deploy/ansible/requirements.yml installed
#
# Usage:
#   ./deploy/ansible/tests/verify-secret-validation.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANSIBLE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PLAYBOOK="${ANSIBLE_PLAYBOOK:-ansible-playbook}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

failures=0

log() { printf '%s\n' "$*"; }
pass() { log "PASS: $*"; }
fail() { log "FAIL: $*"; failures=$((failures + 1)); }

if ! command -v "${PLAYBOOK}" >/dev/null 2>&1; then
  log "ansible-playbook not found (set ANSIBLE_PLAYBOOK or install ansible-core)"
  exit 2
fi

# The interpreter differs by image (slim images only ship /usr/local/bin/python3).
PYTHON_INTERPRETER="$(command -v python3 || echo /usr/bin/python3)"
cat > "${WORK_DIR}/inventory.ini" <<EOF
[accounting]
localhost ansible_connection=local ansible_become=false ansible_python_interpreter=${PYTHON_INTERPRETER}
EOF

if command -v openssl >/dev/null 2>&1; then
  ENC_KEY="$(openssl rand -base64 32)"
else
  # Deterministic, obviously non-secret fallback so this script does not embed
  # base64 literals that secret scanners would flag.
  ENC_KEY="$(printf '%032d' 0 | base64)"
fi

cat > "${WORK_DIR}/empty.yml" <<'EOF'
{}
EOF

cat > "${WORK_DIR}/valid.yml" <<EOF
document_encryption_key: "${ENC_KEY}"
app_setup_enabled: false
EOF

cat > "${WORK_DIR}/valid-key-missing-setup.yml" <<EOF
document_encryption_key: "${ENC_KEY}"
EOF

cat > "${WORK_DIR}/bad-encryption.yml" <<'EOF'
document_encryption_key: "not-a-valid-key"
EOF

# run_playbook <playbook> <extra-vars file> <log name> [additional args...]
# Prints the exit code; the caller checks expected failure/pass.
run_playbook() {
  local playbook="$1"
  local extra="$2"
  local name="$3"
  shift 3
  "${PLAYBOOK}" -i "${WORK_DIR}/inventory.ini" "${ANSIBLE_DIR}/${playbook}" \
    --tags preflight -e "@${extra}" "$@" >"${WORK_DIR}/${name}.log" 2>&1
  local rc=$?
  printf '%s' "${rc}"
  return 0
}

expect_failure() {
  local name="$1" needle="$2"
  if grep -Fq "${needle}" "${WORK_DIR}/${name}.log"; then
    pass "${name}: failed early with expected message"
  else
    fail "${name}: expected message not found: ${needle}"
    sed 's/^/    /' "${WORK_DIR}/${name}.log" | tail -30
  fi
}

expect_success() {
  local name="$1"
  pass "${name}: validation passed"
}

for playbook in site.yml deploy.yml; do
  if "${PLAYBOOK}" -i "${WORK_DIR}/inventory.ini" "${ANSIBLE_DIR}/${playbook}" --syntax-check \
      >"${WORK_DIR}/${playbook}.syntax.log" 2>&1; then
    pass "${playbook}: syntax check"
  else
    fail "${playbook}: syntax check"
    sed 's/^/    /' "${WORK_DIR}/${playbook}.syntax.log"
  fi
done

# site.yml: a missing key must fail in pre_tasks.
rc=$(run_playbook site.yml "${WORK_DIR}/empty.yml" site-missing-key)
[ "${rc}" -ne 0 ] && expect_failure site-missing-key "document_encryption_key is required" \
  || fail "site-missing-key: playbook unexpectedly succeeded"

# site.yml: a malformed value must fail with the variable and generation command.
rc=$(run_playbook site.yml "${WORK_DIR}/bad-encryption.yml" site-bad-encryption)
[ "${rc}" -ne 0 ] && expect_failure site-bad-encryption "document_encryption_key must be a Base64-encoded 32-byte key" \
  || fail "site-bad-encryption: playbook unexpectedly succeeded"

# site.yml: a valid key reaches the end of the tagged preflight tasks.
rc=$(run_playbook site.yml "${WORK_DIR}/valid.yml" site-valid)
[ "${rc}" -eq 0 ] && expect_success site-valid \
  || { fail "site-valid: playbook failed"; sed 's/^/    /' "${WORK_DIR}/site-valid.log" | tail -30; }

# deploy.yml: the same checks must be wired there too.
rc=$(run_playbook deploy.yml "${WORK_DIR}/empty.yml" deploy-missing-key)
[ "${rc}" -ne 0 ] && expect_failure deploy-missing-key "document_encryption_key is required" \
  || fail "deploy-missing-key: playbook unexpectedly succeeded"

rc=$(run_playbook deploy.yml "${WORK_DIR}/valid.yml" deploy-valid)
[ "${rc}" -eq 0 ] && expect_success deploy-valid \
  || { fail "deploy-valid: playbook failed"; sed 's/^/    /' "${WORK_DIR}/deploy-valid.log" | tail -30; }

# A valid key but no app_setup_enabled must also fail, with an actionable message.
rc=$(run_playbook site.yml "${WORK_DIR}/valid-key-missing-setup.yml" site-missing-setup)
[ "${rc}" -ne 0 ] && expect_failure site-missing-setup "app_setup_enabled is not set" \
  || fail "site-missing-setup: playbook unexpectedly succeeded"

# Render the real env.conf template with a valid key and prove the variable is
# exported. This is the no-live-host substitute for reaching application startup.
cat > "${WORK_DIR}/render-env.yml" <<EOF
---
- name: Render env.conf with the production secret
  hosts: localhost
  connection: local
  gather_facts: false
  vars:
    app_name: accounting-finance
    document_encryption_key: "${ENC_KEY}"
    app_setup_enabled: false
  tasks:
    - name: Render env.conf
      ansible.builtin.template:
        src: "${ANSIBLE_DIR}/roles/app/templates/env.conf.j2"
        dest: "${WORK_DIR}/env.conf"
        mode: "0600"
EOF

if "${PLAYBOOK}" -i "${WORK_DIR}/inventory.ini" "${WORK_DIR}/render-env.yml" \
    >"${WORK_DIR}/render-env.log" 2>&1; then
  if grep -Fq "APP_ENCRYPTION_KEY=${ENC_KEY}" "${WORK_DIR}/env.conf"; then
    pass "env.conf: APP_ENCRYPTION_KEY rendered"
  else
    fail "env.conf: expected variable missing"
    sed 's/^/    /' "${WORK_DIR}/env.conf"
  fi
else
  fail "env.conf: template render failed"
  sed 's/^/    /' "${WORK_DIR}/render-env.log"
fi

if [ "${failures}" -gt 0 ]; then
  log ""
  log "FAILED: ${failures} check(s) failed"
  exit 1
fi

log ""
log "All secret-validation checks passed."
