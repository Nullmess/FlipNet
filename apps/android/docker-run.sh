#!/bin/sh
# Compile et installe l'application Android dans Docker.
set -eu

MODE=${1:-build}
ROOT=${FLIPNET_ROOT:-/workspace}
APP_DIR="$ROOT/apps/android"
SOURCE_DIR="$APP_DIR/app/src/main"
JAVA_DIR="$SOURCE_DIR/java"
RES_DIR="$SOURCE_DIR/res"
MANIFEST="$SOURCE_DIR/AndroidManifest.xml"
OUT_DIR="$APP_DIR/dist"
APK="$OUT_DIR/flipnet-debug.apk"
TMP_APK=/tmp/flipnet-debug.apk
PACKAGE=com.flipnet.bridge
PACKAGE_PATH=com/flipnet/bridge
MIN_SDK=${FLIPNET_ANDROID_MIN_SDK:-26}
COMPILE_SDK=${FLIPNET_ANDROID_COMPILE_SDK:-35}
BUILD_TOOLS=${FLIPNET_ANDROID_BUILD_TOOLS:-35.0.0}
SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk-linux}}
PLATFORM_DIR="$SDK_ROOT/platforms/android-$COMPILE_SDK"
TOOLS_DIR="$SDK_ROOT/build-tools/$BUILD_TOOLS"
ANDROID_JAR="$PLATFORM_DIR/android.jar"
AAPT2="$TOOLS_DIR/aapt2"
D8="$TOOLS_DIR/d8"
ZIPALIGN="$TOOLS_DIR/zipalign"
APKSIGNER="$TOOLS_DIR/apksigner"
ADB="$SDK_ROOT/platform-tools/adb"
BUILD_DIR="$APP_DIR/.docker-build"
GENERATED_DIR="$BUILD_DIR/generated"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
COMPILED_RES_DIR="$BUILD_DIR/compiled-res"
RESOURCES_LIST="$BUILD_DIR/resources.list"
RESOURCE_APK="$BUILD_DIR/resources.apk"
UNALIGNED_APK="$BUILD_DIR/unaligned.apk"
ALIGNED_APK="$BUILD_DIR/aligned.apk"
CLASSES_JAR="$BUILD_DIR/classes.jar"
SOURCES_LIST="$BUILD_DIR/sources.list"
SIGNING_STORE=${FLIPNET_SIGNING_STORE:-/tmp/android-signing/flipnet-debug.p12}
SIGNING_ALIAS=${FLIPNET_SIGNING_ALIAS:-flipnet-debug}
SIGNING_PASSWORD=${FLIPNET_SIGNING_PASSWORD:-flipnet-debug}
BUILD_OK=0
owner=$(stat -c '%u:%g' "$APP_DIR" 2>/dev/null || true)

# Arrete le controle avec une erreur claire.
fail() {
    echo "Android: $*" >&2
    exit 1
}

# Verifie les outils presents dans l'image.
check_toolchain() {
    [ -f "$ANDROID_JAR" ] || fail "plateforme Android $COMPILE_SDK absente"
    [ -x "$AAPT2" ] || fail "aapt2 $BUILD_TOOLS absent"
    [ -x "$D8" ] || fail "d8 $BUILD_TOOLS absent"
    [ -x "$ZIPALIGN" ] || fail "zipalign $BUILD_TOOLS absent"
    [ -x "$APKSIGNER" ] || fail "apksigner $BUILD_TOOLS absent"
    command -v javac >/dev/null 2>&1 || fail "javac absent"
    command -v jar >/dev/null 2>&1 || fail "jar absent"
    command -v keytool >/dev/null 2>&1 || fail "keytool absent"
}

# Verifie la coherence du projet avant la compilation.
validate_project() {
    [ -f "$MANIFEST" ] || fail "AndroidManifest.xml absent"
    [ -d "$JAVA_DIR/$PACKAGE_PATH" ] || fail "sources Java absentes pour $PACKAGE"
    if ! grep -Eq 'package[[:space:]]*=[[:space:]]*"com\.flipnet\.bridge"' "$MANIFEST"; then
        fail "le manifeste doit declarer package=\"$PACKAGE\""
    fi
}

# Lit la version publique et calcule le code Android.
read_version() {
    VERSION_NAME=$(tr -d '[:space:]' < "$ROOT/VERSION")
    old_ifs=$IFS
    IFS=.
    set -- $VERSION_NAME
    IFS=$old_ifs
    [ "$#" -eq 3 ] || fail "VERSION doit utiliser major.minor.patch"
    case "$1$2$3" in
        *[!0-9]*) fail "VERSION invalide: $VERSION_NAME" ;;
    esac
    VERSION_CODE=$((100000 + $1 * 10000 + $2 * 100 + $3))
    export VERSION_NAME VERSION_CODE
}

# Supprime les fichiers temporaires.
clean_temporary() {
    rm -rf "$BUILD_DIR" "$APP_DIR/.gradle" "$APP_DIR/build" \
        "$APP_DIR/app/build"
}

# Prepare la cle de signature Android.
prepare_signing() {
    mkdir -p "$(dirname "$SIGNING_STORE")"
    if [ ! -f "$SIGNING_STORE" ]; then
        keytool -genkeypair -noprompt \
            -keystore "$SIGNING_STORE" \
            -storetype PKCS12 \
            -storepass "$SIGNING_PASSWORD" \
            -keypass "$SIGNING_PASSWORD" \
            -alias "$SIGNING_ALIAS" \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -dname "CN=FlipNet Debug,O=FlipNet,C=FR" >/dev/null
        chmod 600 "$SIGNING_STORE"
        echo "Android signing key created."
    fi
    keytool -list \
        -storetype PKCS12 \
        -keystore "$SIGNING_STORE" \
        -storepass "$SIGNING_PASSWORD" \
        -alias "$SIGNING_ALIAS" >/dev/null 2>&1
}

# Genere les constantes habituellement creees par le plugin Android.
generate_build_config() {
    package_dir="$GENERATED_DIR/com/flipnet/bridge"
    mkdir -p "$package_dir"
    cat > "$package_dir/BuildConfig.java" <<CONFIG
package com.flipnet.bridge;

public final class BuildConfig {
    public static final boolean DEBUG = true;
    public static final String APPLICATION_ID = "$PACKAGE";
    public static final String BUILD_TYPE = "debug";
    public static final int VERSION_CODE = $VERSION_CODE;
    public static final String VERSION_NAME = "$VERSION_NAME";

    private BuildConfig() {
    }
}
CONFIG
}

# Compile les ressources et genere R.java.
compile_resources() {
    mkdir -p "$GENERATED_DIR" "$COMPILED_RES_DIR"
    "$AAPT2" compile \
        --dir "$RES_DIR" \
        -o "$COMPILED_RES_DIR"
    find "$COMPILED_RES_DIR" \
        -type f -name '*.flat' -print | sort > "$RESOURCES_LIST"
    [ -s "$RESOURCES_LIST" ] || fail "aucune ressource Android compilee"
    set -- \
        "$AAPT2" link \
        -I "$ANDROID_JAR" \
        --manifest "$MANIFEST" \
        --java "$GENERATED_DIR" \
        --custom-package "$PACKAGE" \
        --rename-manifest-package "$PACKAGE" \
        --min-sdk-version "$MIN_SDK" \
        --target-sdk-version "$COMPILE_SDK" \
        --version-code "$VERSION_CODE" \
        --version-name "$VERSION_NAME" \
        -o "$RESOURCE_APK"
    while IFS= read -r resource; do
        set -- "$@" "$resource"
    done < "$RESOURCES_LIST"
    "$@"
}

# Compile le Java puis produit classes.dex.
compile_java() {
    mkdir -p "$CLASSES_DIR" "$DEX_DIR"
    find "$JAVA_DIR" "$GENERATED_DIR" \
        -type f -name '*.java' -print | sort > "$SOURCES_LIST"
    [ -s "$SOURCES_LIST" ] || fail "aucune source Java trouvee"
    javac \
        -encoding UTF-8 \
        --release 8 \
        -Xlint:deprecation \
        -Xlint:-options \
        -Werror \
        -classpath "$ANDROID_JAR" \
        -d "$CLASSES_DIR" \
        @"$SOURCES_LIST"
    jar cf "$CLASSES_JAR" -C "$CLASSES_DIR" .
    "$D8" \
        --release \
        --min-api "$MIN_SDK" \
        --lib "$ANDROID_JAR" \
        --output "$DEX_DIR" \
        "$CLASSES_JAR"
    [ -f "$DEX_DIR/classes.dex" ] || fail "classes.dex non genere"
}

# Assemble, aligne et signe l'APK.
package_apk() {
    cp "$RESOURCE_APK" "$UNALIGNED_APK"
    jar uf "$UNALIGNED_APK" -C "$DEX_DIR" classes.dex
    "$ZIPALIGN" -f 4 "$UNALIGNED_APK" "$ALIGNED_APK"
    "$APKSIGNER" sign \
        --ks "$SIGNING_STORE" \
        --ks-type PKCS12 \
        --ks-key-alias "$SIGNING_ALIAS" \
        --ks-pass "pass:$SIGNING_PASSWORD" \
        --key-pass "pass:$SIGNING_PASSWORD" \
        --out "$TMP_APK" \
        "$ALIGNED_APK"
    "$APKSIGNER" verify --verbose "$TMP_APK" >/dev/null
}

# Finalise la compilation Android.
finish() {
    clean_temporary
    rm -f "$TMP_APK"
    if [ "$BUILD_OK" -ne 1 ]; then
        rm -rf "$OUT_DIR"
    fi
}

# Compile l'APK Android sans Gradle ni acces reseau.
build_apk() {
    rm -rf "$OUT_DIR"
    clean_temporary
    mkdir -p "$BUILD_DIR"
    check_toolchain
    validate_project
    read_version
    prepare_signing
    generate_build_config
    compile_resources
    compile_java
    package_apk
    mkdir -p "$OUT_DIR"
    cp "$TMP_APK" "$APK"
    if [ -n "$owner" ]; then
        chown -R "$owner" "$OUT_DIR" 2>/dev/null || true
    fi
    BUILD_OK=1
    echo "APK: apps/android/dist/flipnet-debug.apk"
}

# Execute une commande ADB.
adb_run() {
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        "$ADB" -s "$ANDROID_SERIAL" "$@"
    else
        "$ADB" "$@"
    fi
}

# Choisit l'appareil Android.
select_device() {
    attempts=0
    while [ "$attempts" -lt 15 ]; do
        output=$("$ADB" devices 2>&1 || true)
        devices=$(printf '%s\n' "$output" | awk 'NR > 1 && $2 == "device" {print $1}')
        count=$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')
        if [ -n "${ANDROID_SERIAL:-}" ]; then
            if printf '%s\n' "$devices" | grep -Fxq "$ANDROID_SERIAL"; then
                return 0
            fi
            fail "appareil indisponible: $ANDROID_SERIAL"
        fi
        if [ "$count" -eq 1 ]; then
            ANDROID_SERIAL=$devices
            export ANDROID_SERIAL
            return 0
        fi
        if [ "$count" -gt 1 ]; then
            fail "plusieurs appareils; definir ANDROID_SERIAL"
        fi
        if printf '%s\n' "$output" | grep -Eq '[[:space:]](unauthorized|offline)$'; then
            if [ "$attempts" -eq 0 ]; then
                echo "Autoriser le debogage USB sur Android."
            fi
            sleep 1
            attempts=$((attempts + 1))
            continue
        fi
        fail "aucun appareil ADB detecte"
    done
    fail "appareil ADB non autorise"
}

# Confirme une reinstallation incompatible.
confirm_reinstall() {
    case "${FLIPNET_ANDROID_REINSTALL:-}" in
        1|yes|YES|oui|OUI|true|TRUE)
            return 0
            ;;
    esac
    if [ ! -t 0 ]; then
        return 1
    fi
    printf 'Ancienne signature detectee. Reinstaller et supprimer les donnees locales ? [o/N] '
    read -r answer || answer=
    case "$answer" in
        o|O|oui|OUI|y|Y|yes|YES)
            return 0
            ;;
    esac
    return 1
}

# Installe l'APK Android.
install_apk() {
    [ -x "$ADB" ] || fail "adb absent"
    "$ADB" start-server >/dev/null
    select_device
    set +e
    output=$(adb_run install -r "$APK" 2>&1)
    status=$?
    set -e
    printf '%s\n' "$output"
    if [ "$status" -eq 0 ]; then
        echo "Installed: $PACKAGE ($ANDROID_SERIAL)"
        return 0
    fi
    case "$output" in
        *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
            ;;
        *)
            fail "installation impossible"
            ;;
    esac
    if ! confirm_reinstall; then
        fail "migration annulee"
    fi
    adb_run uninstall "$PACKAGE"
    adb_run install "$APK"
    echo "Reinstalled: $PACKAGE ($ANDROID_SERIAL)"
}

trap finish EXIT HUP INT TERM

case "$MODE" in
    build)
        build_apk
        ;;
    install)
        build_apk
        install_apk
        ;;
    *)
        fail "mode inconnu: $MODE"
        ;;
esac
