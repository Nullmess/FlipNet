"""Gère la configuration de la passerelle."""

from __future__ import annotations

import json
import os
import shutil
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any

from .config_migration import (
    CURRENT_CONFIG_SCHEMA,
    ConfigMigrationReport,
    migrate_config_payload,
)
from .permissions import DEFAULT_FEATURE_PERMISSIONS


# Regroupe les réglages de la passerelle.
@dataclass(slots=True)
class BridgeConfig:
    schema_version: int = CURRENT_CONFIG_SCHEMA
    language: str = "fr"
    wikipedia_language: str = "fr"
    web_search_provider: str = "duckduckgo"
    searxng_url: str = ""
    ascii_only: bool = True
    request_timeout_seconds: float = 10.0
    port_scan_timeout_seconds: float = 0.7
    default_rss_source: str = "fr_certfr"
    rss_feeds: list[dict[str, str]] = field(default_factory=list)
    online_apps: list[dict[str, Any]] = field(default_factory=list)
    ai_endpoint: str = ""
    ai_model: str = ""
    ai_api_key_env: str = "FLIPNET_AI_API_KEY"
    monitor_poll_seconds: float = 30.0
    desktop_notifications: bool = True
    state_path: Path | None = None
    data_path: Path | None = None
    config_path: Path | None = None
    secure_actions_enabled: bool = False
    secure_action_ttl_seconds: int = 30
    allowed_service_actions: list[str] = field(default_factory=list)
    allowed_workload_actions: list[str] = field(default_factory=list)
    playbooks: list[dict[str, Any]] = field(default_factory=list)
    allowed_feature_permissions: list[str] = field(
        default_factory=lambda: list(DEFAULT_FEATURE_PERMISSIONS)
    )
    disabled_feature_domains: list[str] = field(default_factory=list)
    disabled_extension_groups: list[str] = field(default_factory=list)
    feature_timeout_cap_seconds: float = 120.0
    plugin_catalog_url: str = ""
    migration_applied: bool = False
    migration_source_schema: int = CURRENT_CONFIG_SCHEMA
    migration_backup_path: Path | None = None


_EDITABLE_FIELDS = (
    "language",
    "wikipedia_language",
    "web_search_provider",
    "searxng_url",
    "ascii_only",
    "request_timeout_seconds",
    "port_scan_timeout_seconds",
    "default_rss_source",
    "rss_feeds",
    "online_apps",
    "ai_endpoint",
    "ai_model",
    "ai_api_key_env",
    "monitor_poll_seconds",
    "desktop_notifications",
    "secure_actions_enabled",
    "secure_action_ttl_seconds",
    "allowed_service_actions",
    "allowed_workload_actions",
    "playbooks",
    "allowed_feature_permissions",
    "disabled_feature_domains",
    "disabled_extension_groups",
    "feature_timeout_cap_seconds",
    "plugin_catalog_url",
)


# Construit les réglages par défaut.
def _default_payload() -> dict[str, Any]:
    return {
        "schema_version": CURRENT_CONFIG_SCHEMA,
        "language": "fr",
        "wikipedia_language": "fr",
        "web_search_provider": "duckduckgo",
        "searxng_url": "",
        "ascii_only": True,
        "request_timeout_seconds": 10,
        "port_scan_timeout_seconds": 0.7,
        "default_rss_source": "fr_certfr",
        "monitor_poll_seconds": 30,
        "desktop_notifications": True,
        "secure_actions_enabled": False,
        "secure_action_ttl_seconds": 30,
        "allowed_service_actions": [],
        "allowed_workload_actions": [],
        "playbooks": [],
        "allowed_feature_permissions": list(DEFAULT_FEATURE_PERMISSIONS),
        "disabled_feature_domains": [],
        "disabled_extension_groups": [],
        "feature_timeout_cap_seconds": 120,
        "plugin_catalog_url": "",
        "rss_feeds": [],
        "online_apps": [],
        "ai_endpoint": "",
        "ai_model": "",
        "ai_api_key_env": "FLIPNET_AI_API_KEY",
    }


# Nettoie une liste de chaînes.
def _string_list(value: Any, maximum: int = 64) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip()[:maximum] for item in value if str(item).strip()][:64]


# Normalise une source de flux.
def _normalise_feed(item: Any, index: int) -> dict[str, str] | None:
    if not isinstance(item, dict):
        return None
    url = str(item.get("url", "")).strip()
    if not url.startswith(("https://", "http://")):
        return None
    identifier = str(item.get("id") or f"custom_{index}").strip().lower()[:64]
    identifier = "".join(
        character for character in identifier if character.isalnum() or character in "._-"
    )
    if not identifier:
        identifier = f"custom_{index}"
    return {
        "id": identifier,
        "language": str(item.get("language", "custom")).strip()[:24] or "custom",
        "category": str(item.get("category", "custom")).strip()[:32] or "custom",
        "title": str(item.get("title", f"Custom {index + 1}")).strip()[:80]
        or f"Custom {index + 1}",
        "url": url[:1000],
    }


# Normalise une application en ligne.
def _normalise_online_app(item: Any, index: int) -> dict[str, Any] | None:
    if not isinstance(item, dict):
        return None
    identifier = str(item.get("id") or f"custom_{index}").strip().lower()[:24]
    identifier = "".join(
        character for character in identifier if character.isalnum() or character == "_"
    )
    if not identifier:
        return None
    url = str(item.get("url") or "").strip()[:2000]
    if not url.startswith(("https://", "http://")):
        return None
    mode = str(item.get("mode", "input")).strip().lower()
    if mode not in {"input", "run"}:
        mode = "input"
    headers: dict[str, str] = {}
    raw_headers = item.get("headers")
    if isinstance(raw_headers, dict):
        for name, value in list(raw_headers.items())[:12]:
            header_name = str(name).strip()[:64]
            header_value = str(value).strip()[:512]
            if header_name and header_value:
                headers[header_name] = header_value
    payload = {
        "id": identifier,
        "title": str(item.get("title") or identifier.replace("_", " ").title()).strip()[:48],
        "mode": mode,
        "prompt": str(item.get("prompt") or "Value").strip()[:48],
        "initial": str(item.get("initial") or "").strip()[:80],
        "url": url,
        "response_path": str(item.get("response_path") or "").strip()[:160],
        "title_path": str(item.get("title_path") or "").strip()[:160],
        "result_title": str(item.get("result_title") or "").strip()[:64],
        "headers": headers,
    }
    for field_name, limit in (("title", 48), ("prompt", 48), ("initial", 80)):
        for language in ("en", "fr"):
            key = f"{field_name}_{language}"
            if key in item:
                payload[key] = str(item.get(key) or "").strip()[:limit]
    return payload


def config_to_payload(config: BridgeConfig) -> dict[str, Any]:
    """Retourne les réglages modifiables."""

    return {
        "schema_version": CURRENT_CONFIG_SCHEMA,
        "language": config.language,
        "wikipedia_language": config.wikipedia_language,
        "web_search_provider": config.web_search_provider,
        "searxng_url": config.searxng_url,
        "ascii_only": config.ascii_only,
        "request_timeout_seconds": config.request_timeout_seconds,
        "port_scan_timeout_seconds": config.port_scan_timeout_seconds,
        "default_rss_source": config.default_rss_source,
        "monitor_poll_seconds": config.monitor_poll_seconds,
        "desktop_notifications": config.desktop_notifications,
        "secure_actions_enabled": config.secure_actions_enabled,
        "secure_action_ttl_seconds": config.secure_action_ttl_seconds,
        "allowed_service_actions": list(config.allowed_service_actions),
        "allowed_workload_actions": list(config.allowed_workload_actions),
        "playbooks": [dict(item) for item in config.playbooks if isinstance(item, dict)],
        "allowed_feature_permissions": list(config.allowed_feature_permissions),
        "disabled_feature_domains": list(config.disabled_feature_domains),
        "disabled_extension_groups": list(config.disabled_extension_groups),
        "feature_timeout_cap_seconds": config.feature_timeout_cap_seconds,
        "plugin_catalog_url": config.plugin_catalog_url,
        "rss_feeds": [dict(item) for item in config.rss_feeds],
        "online_apps": [dict(item) for item in config.online_apps],
        "ai_endpoint": config.ai_endpoint,
        "ai_model": config.ai_model,
        "ai_api_key_env": config.ai_api_key_env,
    }


def apply_config_payload(config: BridgeConfig, raw: dict[str, Any]) -> BridgeConfig:
    """Valide puis applique les réglages."""

    if not isinstance(raw, dict):
        raise ValueError("configuration must be an object")
    config.language = str(raw.get("language", config.language)).strip()[:12] or "fr"
    config.wikipedia_language = (
        str(raw.get("wikipedia_language", config.wikipedia_language)).strip()[:12] or "fr"
    )
    provider = str(raw.get("web_search_provider", config.web_search_provider)).strip().lower()
    config.web_search_provider = provider if provider in {"duckduckgo", "searxng"} else "duckduckgo"
    searxng_url = str(raw.get("searxng_url", config.searxng_url)).strip()[:1000]
    config.searxng_url = (
        searxng_url.rstrip("/") if searxng_url.startswith(("https://", "http://")) else ""
    )
    config.ascii_only = bool(raw.get("ascii_only", config.ascii_only))
    config.request_timeout_seconds = max(
        1.0, min(float(raw.get("request_timeout_seconds", config.request_timeout_seconds)), 120.0)
    )
    config.port_scan_timeout_seconds = max(
        0.05,
        min(float(raw.get("port_scan_timeout_seconds", config.port_scan_timeout_seconds)), 10.0),
    )
    config.default_rss_source = (
        str(raw.get("default_rss_source", config.default_rss_source)).strip()[:64] or "fr_certfr"
    )
    config.monitor_poll_seconds = max(
        15.0, min(float(raw.get("monitor_poll_seconds", config.monitor_poll_seconds)), 3600.0)
    )
    config.desktop_notifications = bool(
        raw.get("desktop_notifications", config.desktop_notifications)
    )
    config.secure_actions_enabled = bool(
        raw.get("secure_actions_enabled", config.secure_actions_enabled)
    )
    config.secure_action_ttl_seconds = max(
        10, min(int(raw.get("secure_action_ttl_seconds", config.secure_action_ttl_seconds)), 120)
    )
    config.allowed_service_actions = _string_list(
        raw.get("allowed_service_actions", config.allowed_service_actions)
    )
    config.allowed_workload_actions = _string_list(
        raw.get("allowed_workload_actions", config.allowed_workload_actions)
    )
    raw_playbooks = raw.get("playbooks", config.playbooks)
    config.playbooks = raw_playbooks if isinstance(raw_playbooks, list) else []
    config.allowed_feature_permissions = _string_list(
        raw.get("allowed_feature_permissions", config.allowed_feature_permissions)
    )
    config.disabled_feature_domains = _string_list(
        raw.get("disabled_feature_domains", config.disabled_feature_domains)
    )
    config.disabled_extension_groups = _string_list(
        raw.get("disabled_extension_groups", config.disabled_extension_groups)
    )
    config.feature_timeout_cap_seconds = max(
        1.0,
        min(
            float(
                raw.get("feature_timeout_cap_seconds", config.feature_timeout_cap_seconds)
            ),
            120.0,
        ),
    )
    catalog_url = str(raw.get("plugin_catalog_url", config.plugin_catalog_url)).strip()[:2000]
    config.plugin_catalog_url = (
        catalog_url if catalog_url.startswith(("https://", "http://")) else ""
    )
    feeds: list[dict[str, str]] = []
    for index, item in enumerate(raw.get("rss_feeds", config.rss_feeds)):
        normalised = _normalise_feed(item, index)
        if normalised is not None:
            feeds.append(normalised)
    config.rss_feeds = feeds[:64]
    online_apps: list[dict[str, Any]] = []
    for index, item in enumerate(raw.get("online_apps", config.online_apps)):
        normalised_app = _normalise_online_app(item, index)
        if normalised_app is not None:
            online_apps.append(normalised_app)
    config.online_apps = online_apps[:16]
    ai_endpoint = str(raw.get("ai_endpoint", config.ai_endpoint)).strip()[:2000]
    config.ai_endpoint = ai_endpoint if ai_endpoint.startswith(("https://", "http://")) else ""
    config.ai_model = str(raw.get("ai_model", config.ai_model)).strip()[:120]
    env_name = str(raw.get("ai_api_key_env", config.ai_api_key_env)).strip()[:80]
    config.ai_api_key_env = (
        env_name if env_name.replace("_", "").isalnum() else "FLIPNET_AI_API_KEY"
    )
    return config


# Limite les droits du fichier.
def _restrict_permissions(path: Path) -> None:
    try:
        path.chmod(0o600)
    except OSError:
        pass


# Crée la configuration initiale.
def create_default_config(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(_default_payload(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _restrict_permissions(path)


def save_config(path: Path, config: BridgeConfig) -> None:
    """Enregistre la configuration atomiquement."""

    config.schema_version = CURRENT_CONFIG_SCHEMA
    path = path.expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(config_to_payload(config), handle, ensure_ascii=False, indent=2)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    _restrict_permissions(temporary)
    temporary.replace(path)
    _restrict_permissions(path)


# Calcule le chemin de sauvegarde.
def config_backup_path(path: Path) -> Path:
    return path.with_suffix(path.suffix + ".pre-schema-3.bak")


# Valide une configuration sans l écrire.
def validate_config_payload(
    raw: dict[str, Any],
) -> tuple[BridgeConfig, ConfigMigrationReport]:
    migrated, report = migrate_config_payload(raw)
    config = BridgeConfig()
    apply_config_payload(config, migrated)
    config.schema_version = CURRENT_CONFIG_SCHEMA
    config.migration_source_schema = report.source_schema
    return config, report


# Migre un fichier avec une sauvegarde.
def migrate_config_file(
    path: Path,
    *,
    dry_run: bool = False,
) -> ConfigMigrationReport:
    resolved = path.expanduser().resolve()
    raw = json.loads(resolved.read_text(encoding="utf-8"))
    config, report = validate_config_payload(raw)
    if not report.changed or dry_run:
        return report
    backup = config_backup_path(resolved)
    if not backup.exists():
        shutil.copy2(resolved, backup)
        _restrict_permissions(backup)
    config.config_path = resolved
    save_config(resolved, config)
    return replace(report, backup_path=str(backup))


# Charge la configuration enregistrée.
def load_config(path: Path) -> BridgeConfig:
    resolved = path.expanduser().resolve()
    if not resolved.exists():
        create_default_config(resolved)

    raw = json.loads(resolved.read_text(encoding="utf-8"))
    config, report = validate_config_payload(raw)
    config.config_path = resolved
    config.migration_applied = report.changed
    if report.changed:
        migrated = migrate_config_file(resolved)
        if migrated.backup_path:
            config.migration_backup_path = Path(migrated.backup_path)
    return config


__all__ = [
    "BridgeConfig",
    "CURRENT_CONFIG_SCHEMA",
    "ConfigMigrationReport",
    "apply_config_payload",
    "config_to_payload",
    "config_backup_path",
    "create_default_config",
    "load_config",
    "migrate_config_file",
    "save_config",
    "validate_config_payload",
]
