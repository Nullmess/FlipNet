#!/usr/bin/env sh
# Installe le service OpenRC.
set -eu

SERVICE_NAME="flipnet-bridge"
RUN_USER="${SUDO_USER:-${DOAS_USER:-${USER:-}}}"
[ -n "$RUN_USER" ] || RUN_USER=$(id -un)

# Lit passwd/group avec ou sans getent pour les systèmes Alpine minimaux.
passwd_home() {
    user_name=$1
    if command -v getent >/dev/null 2>&1; then
        getent passwd "$user_name" | awk -F: 'NR == 1 {print $6}'
    elif [ -r /etc/passwd ]; then
        awk -F: -v name="$user_name" '$1 == name {print $6; exit}' /etc/passwd
    fi
}

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

RUN_HOME="$(passwd_home "$RUN_USER")"
[ -n "$RUN_HOME" ] || RUN_HOME="${HOME:-}"
if [ -z "$RUN_HOME" ]; then
    echo "Impossible de determiner le HOME de $RUN_USER." >&2
    exit 1
fi
BRIDGE_BIN="${FLIPNET_BRIDGE_BIN:-$(command -v flipnet-bridge || true)}"
BRIDGE_ARGS="${FLIPNET_BRIDGE_ARGS:---transport usb --service-mode}"
LOG_FILE="${FLIPNET_LOG_FILE:-$RUN_HOME/.local/state/flipnet/bridge.log}"
SERIAL_GROUP="${FLIPNET_SERIAL_GROUP:-dialout}"
if ! group_exists "$SERIAL_GROUP"; then
    SERIAL_GROUP="$(id -gn "$RUN_USER")"
fi

if [ -z "$BRIDGE_BIN" ]; then
    BRIDGE_BIN="$RUN_HOME/.local/bin/flipnet-bridge"
fi
if [ ! -x "$BRIDGE_BIN" ]; then
    echo "flipnet-bridge introuvable. Installe d'abord le bridge." >&2
    exit 1
fi

# Exécute une commande avec les droits requis.
privileged() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif command -v doas >/dev/null 2>&1; then
        doas "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        echo "Cette operation exige root, sudo ou doas." >&2
        exit 1
    fi
}

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
cat > "$TMP" <<RC
#!/sbin/openrc-run
name="FlipNet Bridge"
description="Passerelle Internet USB/BLE pour Flipper Zero"
command="$BRIDGE_BIN"
command_args="$BRIDGE_ARGS"
command_user="$RUN_USER:$SERIAL_GROUP"
directory="$RUN_HOME"
export HOME="$RUN_HOME"
supervisor="supervise-daemon"
respawn_delay=3
respawn_max=0
output_log="$LOG_FILE"
error_log="$LOG_FILE"

# Déclare les dépendances OpenRC.
depend() {
    use bluetooth dbus net
    after dbus bluetooth
}
RC

privileged mkdir -p "$(dirname "$LOG_FILE")"
privileged install -m 0755 "$TMP" "/etc/init.d/$SERVICE_NAME"
privileged touch "$LOG_FILE"
privileged chown "$RUN_USER" "$LOG_FILE"
privileged rc-update add "$SERVICE_NAME" default
privileged rc-service "$SERVICE_NAME" restart

echo "Service OpenRC installe et demarre."
echo "Etat : rc-service $SERVICE_NAME status"
echo "Logs : tail -f $LOG_FILE"
