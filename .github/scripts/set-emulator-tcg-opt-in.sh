#!/usr/bin/env bash
set -euo pipefail

kvm_device="${1:-/dev/kvm}"
github_env="${GITHUB_ENV:?GITHUB_ENV must point to the GitHub Actions environment file}"

if [[ -c "$kvm_device" && -r "$kvm_device" && -w "$kvm_device" ]]; then
  echo "::notice::KVM is accessible; the emulator can use hardware acceleration."
else
  echo "::warning::KVM is missing or inaccessible; enabling the Android Emulator TCG opt-in."
  printf '%s\n' 'ANDROID_I_WANT_MY_TCG=yes' >> "$github_env"
fi
