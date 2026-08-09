#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

nix develop --no-update-lock-file --command ./gradlew 'Set active project to 26.2.x'
exec nix develop --no-update-lock-file --command ./gradlew :26.2.x:runClient "$@"
