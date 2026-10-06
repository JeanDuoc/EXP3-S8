#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for nombre in cuentas-service auth-server discovery-server config-server; do
  archivo="logs/$nombre.pid"
  if [[ -f "$archivo" ]]; then
    proceso=$(cat "$archivo")
    if [[ "$proceso" =~ ^[0-9]+$ ]] && ps -p "$proceso" -o args= | grep -F "$PWD/$nombre/target/$nombre-2.0.0.jar" >/dev/null; then
      kill "$proceso"
      echo "$nombre detenido"
    fi
    rm -f "$archivo"
  fi
done
