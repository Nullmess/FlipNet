# Construit l'application Android avec un SDK deja present dans l'image.
FROM alvrme/alpine-android:android-35-jdk17-v2024.08.04

ARG FLIPNET_VERSION=1.0.1

ENV FLIPNET_ANDROID_COMPILE_SDK=35
ENV FLIPNET_ANDROID_BUILD_TOOLS=35.0.0
ENV FLIPNET_ANDROID_MIN_SDK=26

USER root

COPY docker-run.sh /usr/local/bin/flipnet-android

RUN chmod +x /usr/local/bin/flipnet-android \
    && test -f "${ANDROID_HOME}/platforms/android-35/android.jar" \
    && test -x "${ANDROID_HOME}/build-tools/35.0.0/aapt2" \
    && test -x "${ANDROID_HOME}/build-tools/35.0.0/d8" \
    && test -x "${ANDROID_HOME}/build-tools/35.0.0/zipalign" \
    && test -x "${ANDROID_HOME}/build-tools/35.0.0/apksigner" \
    && test -x "${ANDROID_HOME}/platform-tools/adb"

ENTRYPOINT ["/usr/local/bin/flipnet-android"]
