# syntax=docker/dockerfile:1
# Construit l’environnement Flipper.
FROM python:3.12-slim-bookworm

ARG UFBT_VERSION=0.2.6
ARG FLIPNET_VERSION

LABEL org.opencontainers.image.title="FlipNet Flipper builder" \
      org.opencontainers.image.version="${FLIPNET_VERSION}"

ENV HOME=/tmp/flipnet-home \
    UFBT_HOME=/tmp/ufbt \
    FLIPNET_VERSION=${FLIPNET_VERSION} \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_ROOT_USER_ACTION=ignore

# Télécharge uFBT et conserve le SDK en cache.
RUN set -eux; \
    apt-get update; \
    apt-get install --no-install-recommends -y ca-certificates curl; \
    rm -rf /var/lib/apt/lists/*; \
    python -m pip install \
        --no-cache-dir \
        --retries 5 \
        --timeout 120 \
        "ufbt==${UFBT_VERSION}"; \
    python -c 'import importlib.metadata as m; print("uFBT", m.version("ufbt"))'; \
    command -v ufbt; \
    command -v ufbt-bootstrap; \
    command -v curl

COPY scripts/docker/flipper/run.sh /usr/local/bin/flipnet-flipper
RUN chmod +x /usr/local/bin/flipnet-flipper

ENTRYPOINT ["/usr/local/bin/flipnet-flipper"]
