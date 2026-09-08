"""Gère les extensions et les modules externes depuis le Flipper."""

from __future__ import annotations

from collections import defaultdict

from ..core.config import BridgeConfig, save_config
from ..core.platform_paths import default_plugins_path
from ..plugins.package import list_packages, remove_package, set_enabled
from ..registry.generated import BUILTIN_MANIFESTS
from .shared import ServiceError, ServiceResult

MANAGEMENT_SERVICE_IDS = frozenset(
    {
        "extension_manager_list",
        "extension_manager_toggle",
        "plugin_manager_list",
        "plugin_manager_toggle",
        "plugin_manager_remove",
    }
)

_EXTENSION_LABELS = {
    "internet": ("Internet", "Internet"),
    "feeds": ("News and RSS", "Actualites et RSS"),
    "utilities": ("Utilities", "Utilitaires"),
}


def _safe_field(value: object, limit: int = 80) -> str:
    """Nettoie un champ du protocole de gestion."""

    text = str(value or "").replace("|", "/").replace("\r", " ").replace("\n", " ")
    return " ".join(text.split())[:limit]


def _save(config: BridgeConfig) -> None:
    """Enregistre la configuration active."""

    if config.config_path is None:
        raise ServiceError("Chemin de configuration indisponible")
    save_config(config.config_path, config)


def _extension_groups() -> dict[str, tuple]:
    """Regroupe les extensions intégrées."""

    groups: dict[str, list] = defaultdict(list)
    for manifest in BUILTIN_MANIFESTS.values():
        if manifest.origin == "bundled" and manifest.extension_group:
            groups[manifest.extension_group].append(manifest)
    return {key: tuple(values) for key, values in groups.items()}


def _extension_state(group: str, manifests: tuple, config: BridgeConfig) -> tuple[str, str, str]:
    """Calcule l’état visible d’une extension."""

    if group in set(config.disabled_extension_groups):
        return "D", "Disabled", "Desactivee"
    denied = sorted(
        {
            permission
            for manifest in manifests
            for permission in manifest.permissions
            if permission not in set(config.allowed_feature_permissions)
        }
    )
    if denied:
        reason = "Permission denied " + denied[0]
        return "P", reason, "Permission refusee " + denied[0]
    if not any("desktop" in manifest.platforms for manifest in manifests):
        return "U", "Unavailable on desktop", "Indisponible sur desktop"
    return "E", "Enabled", "Activee"


def extension_manager_list(
    argument: str,
    config: BridgeConfig,
) -> ServiceResult:
    """Liste les extensions intégrées."""

    del argument
    lines = ["FNEXT1"]
    for group in sorted(_extension_groups()):
        manifests = _extension_groups()[group]
        state, reason_en, reason_fr = _extension_state(group, manifests, config)
        label_en, label_fr = _EXTENSION_LABELS.get(
            group,
            (group.replace("_", " ").title(), group.replace("_", " ").title()),
        )
        del reason_en, reason_fr
        lines.append(
            "|".join(
                (
                    _safe_field(group, 24),
                    state,
                    _safe_field(label_en, 32),
                    _safe_field(label_fr, 32),
                )
            )
        )
    return ServiceResult("Extension settings", "\n".join(lines) + "\n")


def extension_manager_toggle(
    argument: str,
    config: BridgeConfig,
) -> ServiceResult:
    """Active ou désactive un groupe intégré."""

    group = argument.strip().lower()
    if group not in _extension_groups():
        raise ServiceError("Extension integree inconnue")
    disabled = set(config.disabled_extension_groups)
    if group in disabled:
        disabled.remove(group)
        enabled = True
    else:
        disabled.add(group)
        enabled = False
    config.disabled_extension_groups = sorted(disabled)
    _save(config)
    if enabled:
        return ServiceResult("Extension enabled", group)
    return ServiceResult("Extension disabled", group)


def _package_state(package) -> tuple[str, str, str]:
    """Calcule l’état visible d’un module externe."""

    if package.issues:
        issue = _safe_field(package.issues[0], 80)
        return "B", issue, issue
    if package.enabled:
        return "E", "Enabled", "Active"
    return "D", "Disabled", "Desactive"


def plugin_manager_list(argument: str, config: BridgeConfig) -> ServiceResult:
    """Liste les modules externes installés."""

    del argument, config
    lines = ["FNPLUG1"]
    for package in list_packages(default_plugins_path())[:10]:
        state, reason_en, reason_fr = _package_state(package)
        del reason_en, reason_fr
        lines.append(
            "|".join(
                (
                    _safe_field(package.package_id, 24),
                    state,
                    _safe_field(package.version, 16),
                    _safe_field(package.title, 24),
                    _safe_field(package.title_fr or package.title, 24),
                )
            )
        )
    return ServiceResult("Plugin settings", "\n".join(lines) + "\n")


def _reload_plugins() -> None:
    """Recharge le registre des modules externes."""

    from ..registry.runtime import configure_installed_plugins

    configure_installed_plugins(default_plugins_path())


def plugin_manager_toggle(argument: str, config: BridgeConfig) -> ServiceResult:
    """Active ou désactive un module externe."""

    del config
    package_id = argument.strip().lower()
    package = next(
        (item for item in list_packages(default_plugins_path()) if item.package_id == package_id),
        None,
    )
    if package is None:
        raise ServiceError("Plugin installe introuvable")
    try:
        set_enabled(package_id, default_plugins_path(), not package.enabled)
    except ValueError as exc:
        raise ServiceError(str(exc)) from exc
    _reload_plugins()
    if package.enabled:
        return ServiceResult("Plugin disabled", package_id)
    return ServiceResult("Plugin enabled", package_id)


def plugin_manager_remove(argument: str, config: BridgeConfig) -> ServiceResult:
    """Supprime un module externe."""

    del config
    package_id = argument.strip().lower()
    try:
        removed = remove_package(package_id, default_plugins_path())
    except ValueError as exc:
        raise ServiceError(str(exc)) from exc
    if not removed:
        raise ServiceError("Plugin installe introuvable")
    _reload_plugins()
    return ServiceResult("Plugin removed", package_id)
