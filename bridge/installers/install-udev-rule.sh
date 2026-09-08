#!/usr/bin/env sh
# Installe la règle udev du Flipper.
set -eu

if ! command -v udevadm >/dev/null 2>&1; then
    echo "udevadm est absent. Ce script vise les distributions utilisant udev/eudev." >&2
    echo "Utilise le groupe du port serie ou une regle mdev adaptee a ta distribution." >&2
    exit 1
fi

# Exécute une commande avec les droits requis.
privileged() {
    if [ "$(id -u)" -eq 0 ]; then "$@"
    elif command -v doas >/dev/null 2>&1; then doas "$@"
    elif command -v sudo >/dev/null 2>&1; then sudo "$@"
    else echo "Cette operation exige root, sudo ou doas." >&2; exit 1
    fi
}

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
cat > "$TMP" <<'RULE'
# Port CDC FlipNet
SUBSYSTEM=="tty", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="5740", TAG+="uaccess", MODE="0660"
RULE

privileged install -m 0644 "$TMP" /etc/udev/rules.d/42-flipnet.rules
privileged udevadm control --reload-rules
privileged udevadm trigger

echo "Regle udev installee. Debranche puis rebranche le Flipper Zero."
