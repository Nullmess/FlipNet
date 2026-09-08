"""Construit les réponses FNCAP2."""

from __future__ import annotations

from collections.abc import Iterable

from ..handlers import shared as services
from ..registry.api import FeatureManifest
from .capability_protocol import catalog_payload
from .capability_protocol import parse_page_request
from .config import BridgeConfig


def validate_platform(platform: str) -> None:
    """Valide le nom de la plateforme."""

    if not platform or len(platform) > 15 or not platform.replace("-", "_").isalnum():
        raise services.ServiceError("Plateforme de capacites invalide")


def build_payload(
    config: BridgeConfig,
    manifests: Iterable[FeatureManifest],
    platform: str,
    argument: str,
) -> str:
    """Construit la page FNCAP2 demandée."""

    validate_platform(platform)
    try:
        request = parse_page_request(argument)
        return catalog_payload(config, tuple(manifests), platform, request.page)
    except ValueError as exc:
        raise services.ServiceError(str(exc)) from exc
