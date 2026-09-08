#!/usr/bin/env sh
# Désinstalle le service OpenRC.
set -eu
# Exécute une commande avec les droits requis.
privileged() {
    if [ "$(id -u)" -eq 0 ]; then "$@"
    elif command -v doas >/dev/null 2>&1; then doas "$@"
    elif command -v sudo >/dev/null 2>&1; then sudo "$@"
    else echo "Cette operation exige root, sudo ou doas." >&2; exit 1
    fi
}
privileged rc-service flipnet-bridge stop 2>/dev/null || true
privileged rc-update del flipnet-bridge default 2>/dev/null || true
privileged rm -f /etc/init.d/flipnet-bridge
printf '%s\n' "Service OpenRC FlipNet supprime."
