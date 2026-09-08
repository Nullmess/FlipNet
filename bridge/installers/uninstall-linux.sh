#!/usr/bin/env sh
# Désinstalle la passerelle sur Linux.
set -eu

DATA_HOME=${XDG_DATA_HOME:-"$HOME/.local/share"}
VENV_DIR=${FLIPNET_VENV:-"$DATA_HOME/flipnet/venv"}

if command -v systemctl >/dev/null 2>&1; then
    systemctl --user disable --now flipnet-bridge.service 2>/dev/null || true
    rm -f "$HOME/.config/systemd/user/flipnet-bridge.service"
    systemctl --user daemon-reload 2>/dev/null || true
fi

if command -v rc-service >/dev/null 2>&1; then
    "$(dirname "$0")/uninstall-openrc.sh" 2>/dev/null || true
fi

rm -f "$HOME/.local/bin/flipnet-bridge"
rm -rf "$VENV_DIR"
echo "FlipNet Bridge desinstalle. La configuration utilisateur a ete conservee."
