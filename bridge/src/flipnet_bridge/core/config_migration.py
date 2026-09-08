"""Migre une ancienne configuration prise en charge."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

CURRENT_CONFIG_SCHEMA = 3
PREVIOUS_CONFIG_SCHEMA = 2
UNVERSIONED_CONFIG_SCHEMA = 1
CANONICAL_CONFIG_KEYS = {
    "schema_version",
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
}


@dataclass(frozen=True, slots=True)
class ConfigMigrationReport:
    """Décrit une migration de configuration."""

    source_schema: int
    target_schema: int
    changed: bool
    renamed_fields: tuple[str, ...]
    ignored_fields: tuple[str, ...]
    backup_path: str = ""


def config_schema(raw: dict[str, Any]) -> int:
    """Lit la version du format."""

    if "schema_version" not in raw:
        return UNVERSIONED_CONFIG_SCHEMA
    value = raw["schema_version"]
    if isinstance(value, bool):
        raise ValueError("configuration schema is invalid")
    try:
        schema = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError("configuration schema is invalid") from exc
    if schema not in {
        UNVERSIONED_CONFIG_SCHEMA,
        PREVIOUS_CONFIG_SCHEMA,
        CURRENT_CONFIG_SCHEMA,
    }:
        raise ValueError("configuration schema is unsupported")
    return schema


def migrate_config_payload(
    raw: dict[str, Any],
) -> tuple[dict[str, Any], ConfigMigrationReport]:
    """Migre les schémas précédents et les fichiers non versionnés."""

    if not isinstance(raw, dict):
        raise ValueError("configuration must be an object")
    source_schema = config_schema(raw)
    unknown = tuple(sorted(set(raw) - CANONICAL_CONFIG_KEYS))
    if unknown and source_schema != UNVERSIONED_CONFIG_SCHEMA:
        raise ValueError("configuration contains unknown fields: " + " ".join(unknown))
    migrated = {
        key: value
        for key, value in raw.items()
        if key in CANONICAL_CONFIG_KEYS and key != "schema_version"
    }
    migrated["schema_version"] = CURRENT_CONFIG_SCHEMA
    return migrated, ConfigMigrationReport(
        source_schema=source_schema,
        target_schema=CURRENT_CONFIG_SCHEMA,
        changed=source_schema != CURRENT_CONFIG_SCHEMA or bool(unknown),
        renamed_fields=(),
        ignored_fields=unknown,
    )


__all__ = [
    "CANONICAL_CONFIG_KEYS",
    "CURRENT_CONFIG_SCHEMA",
    "PREVIOUS_CONFIG_SCHEMA",
    "UNVERSIONED_CONFIG_SCHEMA",
    "ConfigMigrationReport",
    "config_schema",
    "migrate_config_payload",
]
