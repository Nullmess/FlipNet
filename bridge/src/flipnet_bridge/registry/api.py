"""Définit le contrat des fonctionnalités."""

from __future__ import annotations

import re
import sys
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from typing import TYPE_CHECKING

from ..core.config import BridgeConfig
from ..handlers import shared as services

if TYPE_CHECKING:
    from ..core.state import StateStore

FeatureHandler = Callable[[str, BridgeConfig, "StateStore | None"], services.ServiceResult]
_VALID_CATEGORIES = {
    "core",
    "network",
    "systems",
    "operations",
    "workspace",
    "extensions",
}
_VALID_ORIGINS = {"core", "bundled", "external", "installed"}
_SERVICE_ID = re.compile(r"^[a-z0-9_]{1,32}$")


@dataclass(frozen=True, slots=True)
class FeatureManifest:
    """Décrit une fonctionnalité FlipNet."""

    service_id: str
    title: str
    category: str
    permissions: frozenset[str]
    timeout_seconds: float
    origin: str = "external"
    extension_group: str = ""
    platforms: tuple[str, ...] = ("desktop",)
    visible: bool = True
    title_fr: str = ""
    kind: str = "feature"
    domain: str = ""
    group: str = ""
    input_mode: str = "direct"
    prompt_en: str = ""
    prompt_fr: str = ""
    initial: str = ""
    next_service: str = ""


# Associe un manifeste à son gestionnaire.
@dataclass(frozen=True, slots=True)
class Feature:
    manifest: FeatureManifest
    handler: FeatureHandler


class FeatureRegistry:
    """Gère les fonctionnalités disponibles."""

    # Initialise les fonctionnalités disponibles.
    def __init__(self) -> None:
        self._features: dict[str, Feature] = {}

    # Enregistre un service.
    def register(self, feature: Feature) -> None:
        manifest = feature.manifest
        service_id = manifest.service_id
        if not _SERVICE_ID.fullmatch(service_id) or service_id in self._features:
            raise ValueError(f"Fonction invalide ou dupliquee: {manifest.service_id}")
        if manifest.category not in _VALID_CATEGORIES:
            raise ValueError(f"Categorie invalide: {manifest.category}")
        if manifest.origin not in _VALID_ORIGINS:
            raise ValueError(f"Origine invalide: {manifest.origin}")
        if manifest.origin == "bundled" and not manifest.extension_group.strip():
            raise ValueError("Une extension integree doit declarer son groupe")
        if manifest.timeout_seconds <= 0 or manifest.timeout_seconds > 120:
            raise ValueError("Delai de fonction invalide")
        self._features[service_id] = feature

    # Trouve le service demandé.
    def resolve(self, service_id: str) -> Feature | None:
        if not isinstance(service_id, str) or not _SERVICE_ID.fullmatch(service_id):
            return None
        return self._features.get(service_id)

    # Liste les manifestes enregistrés.
    def manifests(self) -> tuple[FeatureManifest, ...]:
        return tuple(feature.manifest for feature in self._features.values())

    def is_available(
        self,
        manifest: FeatureManifest,
        config: BridgeConfig,
    ) -> bool:
        """Indique si une fonction peut être publiée."""

        if manifest.service_id == "capabilities":
            return True
        if manifest.domain in set(config.disabled_feature_domains):
            return False
        if manifest.origin == "bundled" and manifest.extension_group in set(
            config.disabled_extension_groups
        ):
            return False
        return not manifest.permissions.difference(config.allowed_feature_permissions)

    # Liste les fonctions disponibles.
    def available_manifests(
        self,
        config: BridgeConfig,
    ) -> tuple[FeatureManifest, ...]:
        return tuple(
            manifest
            for manifest in self.manifests()
            if self.is_available(manifest, config)
        )

    # Distribue la requête au bon service.
    def dispatch(
        self,
        service_id: str,
        argument: str,
        config: BridgeConfig,
        state: StateStore | None,
    ) -> services.ServiceResult:
        feature = self.resolve(service_id)
        if feature is None:
            raise services.ServiceError(
                f"Service incompatible: {service_id}. Mets a jour la FAP et le compagnon."
            )
        manifest = feature.manifest
        if manifest.service_id == "capabilities":
            return feature.handler(argument, config, state)
        if manifest.domain in set(config.disabled_feature_domains):
            raise services.ServiceError(f"Domaine desactive: {manifest.domain}")
        if manifest.origin == "bundled" and manifest.extension_group in set(
            config.disabled_extension_groups
        ):
            raise services.ServiceError(f"Extension desactivee: {manifest.extension_group}")
        denied = manifest.permissions.difference(config.allowed_feature_permissions)
        if denied:
            raise services.ServiceError("Permissions refusees: " + ", ".join(sorted(denied)))
        return feature.handler(argument, config, state)

    # Calcule le délai autorisé.
    def timeout_for(self, service_id: str, config: BridgeConfig) -> float:
        feature = self.resolve(service_id)
        if feature is None:
            return config.request_timeout_seconds
        return min(feature.manifest.timeout_seconds, config.feature_timeout_cap_seconds)



# Crée une extension sans stockage.
def stateless(handler: Callable[[str, BridgeConfig], services.ServiceResult]) -> FeatureHandler:
    module_name = handler.__module__
    handler_name = handler.__name__

    # Exécute la version active du gestionnaire.
    def run(
        argument: str, config: BridgeConfig, state: StateStore | None
    ) -> services.ServiceResult:
        del state
        current = getattr(sys.modules[module_name], handler_name)
        return current(argument, config)

    return run


# Crée une extension avec stockage.
def stateful(
    handler: Callable[[str, BridgeConfig, StateStore | None], services.ServiceResult],
) -> FeatureHandler:
    module_name = handler.__module__
    handler_name = handler.__name__

    # Exécute la version active du gestionnaire.
    def run(
        argument: str, config: BridgeConfig, state: StateStore | None
    ) -> services.ServiceResult:
        current = getattr(sys.modules[module_name], handler_name)
        return current(argument, config, state)

    return run



# Crée une description d’extension.
def feature(
    service_id: str,
    title: str,
    category: str,
    permissions: Iterable[str],
    timeout: float,
    handler: FeatureHandler,
    *,
    origin: str = "core",
    extension_group: str = "",
    ui_mode: str = "result",
    platforms: tuple[str, ...] = ("desktop",),
    visible: bool = True,
) -> Feature:
    return Feature(
        FeatureManifest(
            service_id=service_id,
            title=title,
            category=category,
            permissions=frozenset(permissions),
            timeout_seconds=timeout,
            origin=origin,
            extension_group=extension_group,
            input_mode=ui_mode,
            platforms=platforms,
            visible=visible,
        ),
        handler,
    )

