"""Produit les diagnostics locaux."""

from __future__ import annotations

import json
import os
import platform
import socket
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

from .. import __version__
from .config import BridgeConfig, validate_config_payload
from .config_migration import CURRENT_CONFIG_SCHEMA
from .data_store import SCHEMA_VERSION as DATA_SCHEMA_VERSION
from .data_store import migrate_payload
from .platform_paths import (
    default_config_path,
    default_data_path,
    default_log_path,
    default_plugins_path,
)


@dataclass(slots=True)
class DiagnosticReport:
    """Regroupe les résultats du diagnostic."""

    version: str
    python: str
    platform: str
    machine: str
    config_exists: bool
    config_valid: bool
    config_schema: int
    config_migration_needed: bool
    data_exists: bool
    data_valid: bool
    data_schema: int
    plugins_directory_exists: bool
    plugin_packages: int
    plugin_issues: int
    log_exists: bool
    serial_support: bool
    bluetooth_support: bool
    internet_dns: bool
    writable_state: bool
    fncap2_self_test: bool
    fnerr1_self_test: bool
    canonical_services: int


# Indique si le transport série est disponible.
def _serial_available() -> bool:
    try:
        import serial  # noqa: F401
    except (ImportError, ValueError):
        return False
    return True


# Vérifie un fichier de configuration.
def _config_status(path: Path) -> tuple[bool, int, bool]:
    if not path.exists():
        return True, CURRENT_CONFIG_SCHEMA, False
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        _, report = validate_config_payload(raw)
    except (OSError, ValueError, json.JSONDecodeError):
        return False, 0, False
    return True, report.source_schema, report.changed


# Vérifie le catalogue persistant.
def _data_status(path: Path) -> tuple[bool, int]:
    if not path.exists():
        return True, DATA_SCHEMA_VERSION
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        migrated = migrate_payload(raw)
    except (OSError, ValueError, json.JSONDecodeError):
        return False, 0
    return True, int(migrated.get("schema_version", 0))


# Vérifie le catalogue de capacités.
def _fncap2_self_test() -> bool:
    try:
        from ..registry.runtime import build_registry
        from .capability_protocol import catalog_pages

        pages = catalog_pages(
            BridgeConfig(),
            build_registry().manifests(),
            "desktop",
        )
    except (OSError, RuntimeError, ValueError):
        return False
    return bool(pages) and all(
        page.startswith("FNCAP2\n") and len(page.encode("utf-8")) <= 1000
        for page in pages
    )


# Vérifie le protocole d erreur.
def _fnerr1_self_test() -> bool:
    try:
        from .error_protocol import ErrorCode, build_error, parse_error

        payload = build_error(
            ErrorCode.SERVICE_NOT_FOUND,
            "service",
            "missing_service",
            True,
            "Service not found",
            "Service introuvable",
        )
        error = parse_error(payload)
    except ValueError:
        return False
    return (
        error.code == ErrorCode.SERVICE_NOT_FOUND
        and error.service == "missing_service"
        and error.recoverable
    )


# Compte les problèmes des paquets externes.
def _plugin_status(path: Path) -> tuple[int, int]:
    try:
        from ..plugins.package import audit_packages

        audits = audit_packages(path)
    except (OSError, ValueError):
        return 0, 1
    issues = sum(1 for item in audits if not item.valid or item.issues)
    return len(audits), issues


# Compte les services canoniques.
def _canonical_service_count() -> int:
    try:
        from ..registry.runtime import build_registry

        return len(build_registry().manifests())
    except (OSError, RuntimeError, ValueError):
        return 0


# Collecte les diagnostics locaux.
def collect_report(
    *,
    config_path: Path | None = None,
    data_path: Path | None = None,
    plugins_path: Path | None = None,
) -> DiagnosticReport:
    config = config_path or default_config_path()
    data = data_path or default_data_path()
    plugins = plugins_path or default_plugins_path()
    log = default_log_path()
    state_dir = log.parent
    try:
        state_dir.mkdir(parents=True, exist_ok=True)
        marker = state_dir / ".doctor-write-test"
        marker.write_text("ok", encoding="utf-8")
        marker.unlink(missing_ok=True)
        writable = True
    except OSError:
        writable = False
    try:
        socket.getaddrinfo("example.org", 443)
        dns = True
    except OSError:
        dns = False
    config_valid, config_schema, migration_needed = _config_status(config)
    data_valid, data_schema = _data_status(data)
    package_count, package_issues = _plugin_status(plugins)
    return DiagnosticReport(
        version=__version__,
        python=platform.python_version(),
        platform=sys.platform,
        machine=platform.machine() or "unknown",
        config_exists=config.exists(),
        config_valid=config_valid,
        config_schema=config_schema,
        config_migration_needed=migration_needed,
        data_exists=data.exists(),
        data_valid=data_valid,
        data_schema=data_schema,
        plugins_directory_exists=plugins.exists(),
        plugin_packages=package_count,
        plugin_issues=package_issues,
        log_exists=log.exists(),
        serial_support=_serial_available(),
        bluetooth_support=bool(os.environ.get("DBUS_SESSION_BUS_ADDRESS"))
        or sys.platform in {"win32", "darwin"},
        internet_dns=dns,
        writable_state=writable,
        fncap2_self_test=_fncap2_self_test(),
        fnerr1_self_test=_fnerr1_self_test(),
        canonical_services=_canonical_service_count(),
    )


# Formate le rapport de diagnostic.
def render_report(report: DiagnosticReport, *, as_json: bool = False) -> str:
    payload = asdict(report)
    if as_json:
        return json.dumps(payload, indent=2, sort_keys=True)
    rows = [f"FlipNet doctor {report.version}"]
    for key, value in payload.items():
        if key == "version":
            continue
        status = "OK" if value is True else "WARN" if value is False else str(value)
        rows.append(f"{key}: {status}")
    return "\n".join(rows)


# Liste les erreurs bloquantes.
def critical_failures(report: DiagnosticReport) -> list[str]:
    failures: list[str] = []
    if not report.writable_state:
        failures.append("state directory is not writable")
    if not report.serial_support:
        failures.append("serial support is unavailable")
    if not report.config_valid:
        failures.append("configuration is invalid")
    if not report.data_valid:
        failures.append("data catalog is invalid")
    if not report.fncap2_self_test:
        failures.append("FNCAP2 self test failed")
    if not report.fnerr1_self_test:
        failures.append("FNERR1 self test failed")
    if report.canonical_services != 67:
        failures.append("canonical service catalog is incomplete")
    if report.plugin_issues:
        failures.append("installed plugins need attention")
    return failures
