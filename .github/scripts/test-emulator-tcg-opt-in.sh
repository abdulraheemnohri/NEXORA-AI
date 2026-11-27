#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
helper="$script_dir/set-emulator-tcg-opt-in.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

env_file="$tmp_dir/github_env"
: > "$env_file"

# /dev/null is a readable/writable character device, so it exercises the
# hardware-available decision without changing the host's actual KVM device.
GITHUB_ENV="$env_file" "$helper" /dev/null
if grep -Fxq 'ANDROID_I_WANT_MY_TCG=yes' "$env_file"; then
  echo "Accessible character device unexpectedly enabled TCG" >&2
  exit 1
fi

# A missing device exercises the no-KVM path and must opt in to TCG.
GITHUB_ENV="$env_file" "$helper" "$tmp_dir/missing-kvm"
grep -Fxq 'ANDROID_I_WANT_MY_TCG=yes' "$env_file"

echo "KVM/TCG opt-in tests passed"
