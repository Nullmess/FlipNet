#!/usr/bin/env sh
# Installe la passerelle sur macOS.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BRIDGE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
APP_HOME="$HOME/Library/Application Support/FlipNet"
VENV_DIR="$APP_HOME/venv"
LOG_FILE="$HOME/Library/Logs/FlipNet/bridge.log"
PLIST="$HOME/Library/LaunchAgents/com.flipnet.bridge.plist"
TRANSPORT=${FLIPNET_TRANSPORT:-usb}

command -v python3 >/dev/null 2>&1 || {
    echo "Python 3.11+ est requis." >&2
    exit 1
}

mkdir -p "$APP_HOME" "$(dirname "$LOG_FILE")" "$(dirname "$PLIST")"
python3 -m venv "$VENV_DIR"
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install --upgrade --force-reinstall "$BRIDGE_DIR"

python3 - "$SCRIPT_DIR/com.flipnet.bridge.plist" "$PLIST" \
    "$VENV_DIR/bin/python" "$TRANSPORT" "$LOG_FILE" <<'PY'
from pathlib import Path
import sys

template = Path(sys.argv[1]).read_text(encoding="utf-8")
destination = Path(sys.argv[2])
values = {
    "__PYTHON__": sys.argv[3],
    "__TRANSPORT__": sys.argv[4],
    "__LOG_FILE__": sys.argv[5],
}
for marker, value in values.items():
    template = template.replace(marker, value)
destination.write_text(template, encoding="utf-8")
PY

launchctl bootout "gui/$(id -u)/com.flipnet.bridge" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
launchctl enable "gui/$(id -u)/com.flipnet.bridge"
launchctl kickstart -k "gui/$(id -u)/com.flipnet.bridge"

echo "FlipNet Bridge installe et lance avec launchd."
echo "Logs : tail -f '$LOG_FILE'"
