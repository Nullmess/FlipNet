#!/usr/bin/env sh
# Installe la passerelle sur Linux.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BRIDGE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ROOT_DIR=$(CDPATH= cd -- "$BRIDGE_DIR/.." && pwd)
PROJECT_VERSION=$(cat "$ROOT_DIR/VERSION")
printf "Installation de FlipNet %s depuis %s\n" "$PROJECT_VERSION" "$ROOT_DIR"
DATA_HOME=${XDG_DATA_HOME:-"$HOME/.local/share"}
STATE_HOME=${XDG_STATE_HOME:-"$HOME/.local/state"}
VENV_DIR=${FLIPNET_VENV:-"$DATA_HOME/flipnet/venv"}
BIN_DIR="$HOME/.local/bin"
LOG_FILE=${FLIPNET_LOG_FILE:-"$STATE_HOME/flipnet/bridge.log"}
TRANSPORT=${FLIPNET_TRANSPORT:-usb}

# Indique si l'hôte utilise Alpine Linux / musl.
is_alpine() {
    [ -f /etc/alpine-release ]
}

# Vérifie l'existence d'un groupe sans dépendre obligatoirement de getent.
group_exists() {
    group_name=$1
    if command -v getent >/dev/null 2>&1; then
        getent group "$group_name" >/dev/null 2>&1
    elif [ -r /etc/group ]; then
        awk -F: -v name="$group_name" '$1 == name {found=1} END {exit !found}' /etc/group
    else
        return 1
    fi
}

# Crée l'environnement Python sur les distributions glibc et musl.
create_venv() {
    VENV_ERROR=$(mktemp)
    if python3 -m venv "$VENV_DIR" 2>"$VENV_ERROR"; then
        rm -f "$VENV_ERROR"
        return 0
    fi

    rm -rf "$VENV_DIR"
    if python3 -m virtualenv --version >/dev/null 2>&1; then
        rm -f "$VENV_ERROR"
        python3 -m virtualenv "$VENV_DIR"
        return 0
    fi

    echo "Impossible de creer l'environnement Python FlipNet." >&2
    cat "$VENV_ERROR" >&2 || true
    rm -f "$VENV_ERROR"
    if is_alpine; then
        echo "Alpine: installe python3, py3-pip et py3-virtualenv avec apk." >&2
        echo "  doas apk add python3 py3-pip py3-virtualenv" >&2
    else
        echo "Installe le module venv/virtualenv de ta distribution puis relance l'installation." >&2
    fi
    exit 1
}

command -v python3 >/dev/null 2>&1 || {
    echo "Erreur: Python 3.11+ est requis." >&2
    exit 1
}

python3 - <<'PY'
import sys
if sys.version_info < (3, 11):
    raise SystemExit("Erreur: FlipNet Bridge requiert Python 3.11 ou plus recent.")
PY

mkdir -p "$DATA_HOME/flipnet" "$BIN_DIR" "$(dirname "$LOG_FILE")"
create_venv
"$VENV_DIR/bin/python" -m pip install --upgrade pip
"$VENV_DIR/bin/python" -m pip install --prefer-binary --upgrade --force-reinstall "$BRIDGE_DIR"
ln -sf "$VENV_DIR/bin/flipnet-bridge" "$BIN_DIR/flipnet-bridge"

CONFIG_HOME=${XDG_CONFIG_HOME:-"$HOME/.config"}
CONFIG_FILE=${FLIPNET_CONFIG:-"$CONFIG_HOME/flipnet/config.json"}
if [ -f "$CONFIG_FILE" ]; then
    echo "Validation et migration de la configuration existante..."
    "$VENV_DIR/bin/python" -m flipnet_bridge config migrate "$CONFIG_FILE"
fi

RUN_USER=${SUDO_USER:-${DOAS_USER:-${USER:-}}}
SERIAL_GROUP=${FLIPNET_SERIAL_GROUP:-}
if [ -z "$SERIAL_GROUP" ]; then
    for device in /dev/ttyACM*; do
        [ -e "$device" ] || continue
        candidate=$(stat -c '%G' "$device" 2>/dev/null || true)
        if [ -n "$candidate" ] && [ "$candidate" != "root" ] \
            && [ "$candidate" != "UNKNOWN" ]; then
            SERIAL_GROUP=$candidate
            break
        fi
    done
fi
if [ -z "$SERIAL_GROUP" ] && group_exists dialout; then
    SERIAL_GROUP=dialout
elif [ -z "$SERIAL_GROUP" ] && group_exists uucp; then
    SERIAL_GROUP=uucp
fi
export FLIPNET_SERIAL_GROUP=$SERIAL_GROUP
if [ "${FLIPNET_SKIP_DEVICE_ACCESS:-0}" != "1" ] && [ -n "$RUN_USER" ]; then
    "$SCRIPT_DIR/install-device-access.sh" "$RUN_USER"
else
    echo "Configuration de l'accès série ignorée."
fi

# Installe le service utilisateur systemd.
install_systemd_user() {
    UNIT_DIR="$HOME/.config/systemd/user"
    UNIT_FILE="$UNIT_DIR/flipnet-bridge.service"
    mkdir -p "$UNIT_DIR"
    python3 - "$SCRIPT_DIR/flipnet-bridge.service" "$UNIT_FILE" \
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
    systemctl --user daemon-reload
    systemctl --user enable flipnet-bridge.service
    systemctl --user restart flipnet-bridge.service
    echo "Service systemd utilisateur installe et demarre."
    echo "Etat : systemctl --user status flipnet-bridge"
    echo "Logs : tail -f $LOG_FILE"
}


# Installe le service OpenRC.
install_openrc() {
    FLIPNET_BRIDGE_BIN="$VENV_DIR/bin/python" \
    FLIPNET_BRIDGE_ARGS="-m flipnet_bridge --transport $TRANSPORT --service-mode --log-file $LOG_FILE" \
    FLIPNET_LOG_FILE="$LOG_FILE" \
    "$SCRIPT_DIR/install-openrc.sh"
}

if [ "${FLIPNET_NO_SERVICE:-0}" = "1" ]; then
    echo "Installation du service ignoree (FLIPNET_NO_SERVICE=1)."
elif command -v systemctl >/dev/null 2>&1 && systemctl --user show-environment >/dev/null 2>&1; then
    install_systemd_user
elif command -v rc-service >/dev/null 2>&1 && command -v rc-update >/dev/null 2>&1; then
    install_openrc
else
    echo "FlipNet Bridge a ete installe, mais aucun gestionnaire systemd/OpenRC exploitable n'a ete detecte."
    echo "Lancement manuel : $BIN_DIR/flipnet-bridge --transport $TRANSPORT --service-mode"
fi

if is_alpine; then
    echo "Alpine Linux detecte: installation musl/OpenRC active."
    if [ "$TRANSPORT" = "ble" ] || [ "$TRANSPORT" = "auto" ]; then
        if ! command -v dbus-daemon >/dev/null 2>&1 || ! command -v bluetoothd >/dev/null 2>&1; then
            echo "BLE: installe dbus et bluez si necessaire : doas apk add dbus bluez"
        fi
    fi
fi

printf '\nInstallation terminee.\n'
printf 'Version : '
"$VENV_DIR/bin/python" -m flipnet_bridge --version
