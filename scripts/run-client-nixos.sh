#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

exec nix develop --no-update-lock-file --command ./gradlew runClient "$@"
