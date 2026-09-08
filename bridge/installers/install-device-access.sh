#!/usr/bin/env sh
# Configure l'accès non privilégié aux ports série FlipNet.
set -eu

RUN_USER=${1:-${SUDO_USER:-${DOAS_USER:-${USER:-}}}}
if [ -z "$RUN_USER" ]; then
    echo "Impossible de déterminer l'utilisateur FlipNet." >&2
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
        echo "Cette opération exige root, sudo ou doas." >&2
        exit 1
    fi
}

# Vérifie un groupe sans imposer getent, absent de certaines installations Alpine minimales.
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

# Choisit le groupe série de la distribution.
serial_group() {
    for device in /dev/ttyACM*; do
        [ -e "$device" ] || continue
        group=$(stat -c '%G' "$device" 2>/dev/null || true)
        if [ -n "$group" ] && [ "$group" != "root" ] && [ "$group" != "UNKNOWN" ]; then
            printf '%s\n' "$group"
            return
        fi
    done
    if group_exists dialout; then
        printf '%s\n' dialout
    elif group_exists uucp; then
        printf '%s\n' uucp
    else
        printf '%s\n' ''
    fi
}

SERIAL_GROUP=${FLIPNET_SERIAL_GROUP:-$(serial_group)}
if [ -z "$SERIAL_GROUP" ]; then
    echo "Aucun groupe série dialout/uucp trouvé; configuration automatique ignorée." >&2
    exit 0
fi

if ! id -nG "$RUN_USER" | tr ' ' '\n' | grep -Fx "$SERIAL_GROUP" >/dev/null 2>&1; then
    if command -v addgroup >/dev/null 2>&1; then
        privileged addgroup "$RUN_USER" "$SERIAL_GROUP"
    elif command -v usermod >/dev/null 2>&1; then
        privileged usermod -a -G "$SERIAL_GROUP" "$RUN_USER"
    else
        echo "Ajoute manuellement $RUN_USER au groupe $SERIAL_GROUP." >&2
    fi
fi

# Installe une règle eudev/udev quand elle est disponible.
install_udev_rule() {
    tmp=$(mktemp)
    cat > "$tmp" <<RULE
# Port CDC FlipNet
SUBSYSTEM=="tty", ATTRS{idVendor}=="0483", ATTRS{idProduct}=="5740", GROUP="$SERIAL_GROUP", MODE="0660", TAG+="uaccess"
RULE
    privileged mkdir -p /etc/udev/rules.d
    privileged install -m 0644 "$tmp" /etc/udev/rules.d/42-flipnet.rules
    rm -f "$tmp"
    privileged udevadm control --reload-rules
    privileged udevadm trigger --subsystem-match=tty || true
}

# Installe une règle mdev/mdevd avant les règles génériques.
install_mdev_rule() {
    current=$(mktemp)
    updated=$(mktemp)
    if [ -f /etc/mdev.conf ]; then
        privileged cat /etc/mdev.conf > "$current"
    fi
    awk '
        $0 == "# BEGIN FLIPNET" {skip=1; next}
        $0 == "# END FLIPNET" {skip=0; next}
        !skip {print}
    ' "$current" > "$updated.body"
    {
        echo "# BEGIN FLIPNET"
        echo "ttyACM[0-9]+ root:$SERIAL_GROUP 660"
        echo "# END FLIPNET"
        cat "$updated.body"
    } > "$updated"
    privileged install -m 0644 "$updated" /etc/mdev.conf
    rm -f "$current" "$updated" "$updated.body"
}

if command -v udevadm >/dev/null 2>&1; then
    install_udev_rule
elif command -v mdev >/dev/null 2>&1 || command -v mdevd >/dev/null 2>&1; then
    install_mdev_rule
fi

# Corrige immédiatement les ports déjà présents.
for device in /dev/ttyACM*; do
    [ -e "$device" ] || continue
    privileged chgrp "$SERIAL_GROUP" "$device" || true
    privileged chmod 0660 "$device" || true
done

printf 'Accès série configuré pour %s via le groupe %s.\n' "$RUN_USER" "$SERIAL_GROUP"
printf 'Débranche puis rebranche le Flipper Zero avant le test.\n'
