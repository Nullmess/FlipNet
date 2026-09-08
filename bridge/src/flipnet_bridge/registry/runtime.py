"""Construit le registre des fonctionnalités."""

from __future__ import annotations

from ..core.capabilities import build_payload
from ..core.config import BridgeConfig
from ..handlers import shared as services
from ..handlers.catalog import builtin_handlers
from .api import Feature, FeatureManifest, FeatureRegistry
from .generated import BUILTIN_MANIFESTS, BUILTIN_SERVICE_IDS


_WIRE_PROTOCOL_PREFIXES = (
    "FNCAP2\n",
    "FNLOC1\n",
    "FNWIKI1\n",
    "FNWEB1\n",
    "FNSEL1\n",
    "FNFEED1\n",
    "FNEXT1\n",
    "FNPLUG1\n",
)


def _normalise_result_body(body: str, ascii_only: bool) -> str:
    """Préserve les séparateurs des catalogues structurés."""

    text = (body or "").replace("\r", "")
    if text.startswith(_WIRE_PROTOCOL_PREFIXES):
        return text
    return services._normalise(text, ascii_only)


def builtin_features() -> tuple[Feature, ...]:
    """Liste les fonctions livrées avec FlipNet."""

    handlers = builtin_handlers()
    expected = set(BUILTIN_SERVICE_IDS)
    current = set(handlers)
    if current != expected:
        missing = " ".join(sorted(expected - current))
        extra = " ".join(sorted(current - expected))
        raise RuntimeError(f"Registre genere divergent absent={missing} extra={extra}")
    return tuple(
        Feature(BUILTIN_MANIFESTS[service_id], handlers[service_id])
        for service_id in BUILTIN_SERVICE_IDS
    )



# Construit le registre actif.
def build_registry(installed_dir=None) -> FeatureRegistry:
    registry = FeatureRegistry()
    for item in builtin_features():
        registry.register(item)
    if installed_dir is not None:
        from ..plugins.runtime import load_plugins

        for item in load_plugins(installed_dir):
            registry.register(item)
    return registry



REGISTRY = build_registry()
SUPPORTED_SERVICES = tuple(manifest.service_id for manifest in REGISTRY.manifests())


def available_manifests(
    config: BridgeConfig
) -> tuple[FeatureManifest, ...]:
    """Liste les fonctions disponibles."""

    return REGISTRY.available_manifests(config)


def capabilities_payload(
    config: BridgeConfig, platform: str = "desktop", argument: str = ""
) -> str:
    """Construit le catalogue filaire des capacités."""

    return build_payload(config, available_manifests(config), platform, argument)


def configure_installed_plugins(installed_dir) -> None:
    """Recharge les extensions déclaratives."""

    global REGISTRY, SUPPORTED_SERVICES
    REGISTRY = build_registry(installed_dir=installed_dir)
    SUPPORTED_SERVICES = tuple(manifest.service_id for manifest in REGISTRY.manifests())


# Distribue la requête au bon service.
def dispatch(
    service_id: str,
    argument: str,
    config: BridgeConfig,
    state=None,
) -> services.ServiceResult:
    result = REGISTRY.dispatch(service_id, argument, config, state)
    return services.ServiceResult(
        services._normalise(result.title, config.ascii_only),
        _normalise_result_body(result.body, config.ascii_only),
    )
