#!/usr/bin/env bash
# Captura a tela do aparelho conectado (só para desenvolvimento) em prints/<nome>.png. prints/ está no .gitignore.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p prints
adb exec-out screencap -p > "prints/${1:?uso: tools/print.sh <nome>}.png"
echo "prints/$1.png"
