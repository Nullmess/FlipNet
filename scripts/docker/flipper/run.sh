#!/bin/sh
# Compile puis lance la FAP dans Docker.
set -eu

ROOT=${FLIPNET_ROOT:-/workspace}
APP_DIR="$ROOT/apps/flipper"
OUT_DIR="$APP_DIR/dist"
TMP_FAP=/tmp/flipnet.fap
HOST_DEV_ROOT=${FLIPNET_HOST_DEV_ROOT:-/host/dev}
HOST_SYS_ROOT=${FLIPNET_HOST_SYS_ROOT:-/host/sys}
BUILD_OK=0
owner=$(stat -c '%u:%g' "$APP_DIR" 2>/dev/null || true)

# Vérifie les outils nécessaires.
require_tools() {
    for tool in python ufbt ufbt-bootstrap; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            echo "Missing container tool: $tool" >&2
            exit 1
        fi
    done
    if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
        echo "Missing container downloader: install curl or wget." >&2
        exit 1
    fi
}

# Prépare le SDK Flipper.
prepare_sdk() {
    mkdir -p "$UFBT_HOME"
    if ufbt-bootstrap status >/dev/null 2>&1; then
        echo "uFBT SDK cache: ready"
        return
    fi
    echo "uFBT SDK cache: empty"
    echo "Downloading the Flipper SDK once; later runs reuse the Docker volume."
    ufbt-bootstrap --verbose update
}

# Supprime les fichiers temporaires.
clean_temporary() {
    rm -rf "$APP_DIR/build" "$APP_DIR/.ufbt"
    find "$APP_DIR" -maxdepth 1 -type f \
        \( -name '.sconsign*' -o -name '*.pyc' \) -delete
}

# Finalise la sortie de compilation.
finish_build() {
    clean_temporary
    rm -f "$TMP_FAP"
    if [ "$BUILD_OK" -ne 1 ]; then
        rm -rf "$OUT_DIR"
    fi
}

# Trouve la FAP compilée.
find_fap() {
    find "$OUT_DIR" -maxdepth 1 -type f -name '*.fap' -print -quit
}

# Compile la FAP.
build_fap() {
    trap finish_build EXIT HUP INT TERM
    rm -rf "$OUT_DIR"
    clean_temporary
    prepare_sdk
    cd "$APP_DIR"
    ufbt
    FAP=$(find_fap)
    if [ -z "$FAP" ]; then
        echo "FAP build failed: no .fap file was generated." >&2
        exit 1
    fi
    cp "$FAP" "$TMP_FAP"
    clean_temporary
    rm -rf "$OUT_DIR"
    mkdir -p "$OUT_DIR"
    cp "$TMP_FAP" "$OUT_DIR/flipnet.fap"
    if [ -n "$owner" ]; then
        chown -R "$owner" "$OUT_DIR" 2>/dev/null || true
    fi
    BUILD_OK=1
    echo "FAP: apps/flipper/dist/flipnet.fap"
}

# Trouve le numéro de série du port USB.
usb_serial_for_tty() {
    tty_name=$1
    sys_path=$(readlink -f "$HOST_SYS_ROOT/class/tty/$tty_name/device" 2>/dev/null || true)
    while [ -n "$sys_path" ] && [ "$sys_path" != "/" ]; do
        if [ -r "$sys_path/serial" ]; then
            cat "$sys_path/serial"
            return 0
        fi
        parent=${sys_path%/*}
        if [ "$parent" = "$sys_path" ]; then
            break
        fi
        sys_path=$parent
    done
    return 1
}

# Expose le port série de la machine hôte.
expose_host_tty() {
    host_device=$1
    tty_name=${host_device##*/}
    container_device="/dev/$tty_name"

    if [ ! -e "$container_device" ]; then
        ln -s "$host_device" "$container_device"
    fi
    printf '%s\n' "$container_device"
}

# Prépare le port série du Flipper.
prepare_flipper_device() {
    if [ -n "${FLIPPER_PATH:-}" ]; then
        if [ -e "$FLIPPER_PATH" ]; then
            echo "Flipper serial device: $FLIPPER_PATH"
            return 0
        fi
        echo "Configured FLIPPER_PATH does not exist: $FLIPPER_PATH" >&2
        return 1
    fi

    if [ -n "${FLIPNET_FLIPPER_DEVICE:-}" ]; then
        requested=$FLIPNET_FLIPPER_DEVICE
        case "$requested" in
            /dev/*)
                host_requested="$HOST_DEV_ROOT/${requested#/dev/}"
                ;;
            *)
                host_requested=$requested
                ;;
        esac
        if [ ! -e "$host_requested" ]; then
            echo "Configured host Flipper device does not exist: $requested" >&2
            return 1
        fi
        FLIPPER_PATH=$(expose_host_tty "$host_requested")
        export FLIPPER_PATH
        echo "Flipper serial device: $FLIPPER_PATH"
        return 0
    fi

    found_path=
    found_serial=
    unidentified_path=
    candidate_count=0

    for host_device in "$HOST_DEV_ROOT"/ttyACM*; do
        [ -e "$host_device" ] || continue
        candidate_count=$((candidate_count + 1))
        container_device=$(expose_host_tty "$host_device")
        tty_name=${host_device##*/}
        serial=$(usb_serial_for_tty "$tty_name" 2>/dev/null || true)
        if [ -n "$serial" ]; then
            echo "USB serial candidate: $container_device ($serial)"
        else
            echo "USB serial candidate: $container_device (serial metadata unavailable)"
            unidentified_path=$container_device
        fi
        case "$serial" in
            flip_*)
                if [ -z "$found_serial" ]; then
                    found_serial=$serial
                    found_path=$container_device
                elif [ "$serial" != "$found_serial" ]; then
                    echo "More than one physical Flipper is attached." >&2
                    return 1
                fi
                ;;
        esac
    done

    if [ -n "$found_path" ]; then
        FLIPPER_PATH=$found_path
        export FLIPPER_PATH
        echo "Flipper serial device: $FLIPPER_PATH"
        return 0
    fi

    if [ "$candidate_count" -eq 1 ] && [ -n "$unidentified_path" ]; then
        FLIPPER_PATH=$unidentified_path
        export FLIPPER_PATH
        echo "USB serial metadata is unavailable; using the only ttyACM device."
        echo "Flipper serial device: $FLIPPER_PATH"
        return 0
    fi

    echo "No connected Flipper was visible inside the launch container." >&2
    if [ "$candidate_count" -eq 0 ]; then
        echo "No /dev/ttyACM* device was exposed by the Linux host." >&2
    else
        echo "The visible ttyACM device(s) did not report a serial beginning with flip_." >&2
    fi
    echo "Check that the Flipper is powered on and connected with a USB data cable." >&2
    echo "Close qFlipper or any serial monitor, reconnect the cable, then retry." >&2
    echo "Host check: ls -l /dev/ttyACM* /dev/serial/by-id 2>/dev/null" >&2
    echo "Manual override: docker compose run --rm --build -e FLIPNET_FLIPPER_DEVICE=/dev/ttyACM0 flipper-launch" >&2
    echo "Rootless Docker and Docker Desktop require separate USB device attachment." >&2
    return 1
}

# Installe puis lance la FAP.
launch_fap() {
    rm -rf "$OUT_DIR"
    clean_temporary
    trap 'clean_temporary; rm -rf "$OUT_DIR" "$TMP_FAP"' EXIT HUP INT TERM
    prepare_flipper_device
    prepare_sdk
    cd "$APP_DIR"
    ufbt launch "FLIP_PORT=$FLIPPER_PATH"
}

require_tools
case "${1:-}" in
    build)
        build_fap
        ;;
    launch)
        launch_fap
        ;;
    *)
        echo "Usage: flipnet-flipper {build|launch}" >&2
        exit 2
        ;;
esac
