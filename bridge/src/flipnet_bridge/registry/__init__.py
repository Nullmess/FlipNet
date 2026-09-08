"""Expose le registre public des fonctionnalités."""

from __future__ import annotations

from .api import Feature, FeatureHandler, FeatureManifest, FeatureRegistry

_RUNTIME_EXPORTS = {
    "builtin_features",
    "build_registry",
    "capabilities_payload",
    "dispatch",
}


def __getattr__(name: str):
    """Charge le moteur seulement lors de son premier usage."""

    if name not in _RUNTIME_EXPORTS:
        raise AttributeError(name)
    from . import runtime

    return getattr(runtime, name)


__all__ = [
    "Feature",
    "FeatureHandler",
    "FeatureManifest",
    "FeatureRegistry",
    "builtin_features",
    "build_registry",
    "capabilities_payload",
    "dispatch",
]
