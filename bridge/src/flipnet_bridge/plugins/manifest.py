"""Valide les manifestes des paquets externes."""

from __future__ import annotations

import re
import urllib.parse
from collections.abc import Iterable
from typing import Any

from .. import __version__
from ..core.permissions import CANONICAL_PERMISSIONS

PACKAGE_SCHEMA = 2
MAX_PACKAGE_SERVICES = 10
SUPPORTED_PACKAGE_SCHEMAS = frozenset({2})
PACKAGE_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_]{0,63}$")
SERVICE_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_]{0,23}$")
CONFIG_KEY_RE = re.compile(r"^[a-z][a-z0-9_]{0,31}$")
HEADER_NAME_RE = re.compile(r"^[A-Za-z0-9-]{1,48}$")
CONFIG_TOKEN_RE = re.compile(r"\{config\.([a-z][a-z0-9_]{0,31})\}")
_ALLOWED_CATEGORIES = {"network", "systems", "operations", "workspace", "extensions"}
_ALLOWED_PERMISSIONS = CANONICAL_PERMISSIONS
_ALLOWED_CONFIG_TYPES = {"text", "secret", "integer", "boolean", "choice"}
_ALLOWED_METHODS = {"GET", "POST"}
_ALLOWED_PLATFORMS = {"desktop"}
_ALLOWED_UI_MODES = {"direct", "text"}


def parse_semver(value: Any) -> tuple[int, int, int]:
    """Convertit une version sémantique stricte."""

    text = str(value or "").strip()
    parts = text.split(".")
    if len(parts) != 3 or any(not part.isdecimal() for part in parts):
        raise ValueError("La version doit suivre major.minor.patch")
    return tuple(int(part) for part in parts)


def normalise_semver(value: Any) -> str:
    """Retourne une version sémantique normalisée."""

    return ".".join(str(part) for part in parse_semver(value))


def version_is_compatible(
    version: str,
    minimum: str,
    maximum: str = "",
) -> bool:
    """Vérifie une plage de versions inclusive."""

    current = parse_semver(version)
    if current < parse_semver(minimum):
        return False
    return not maximum or current <= parse_semver(maximum)


def _clean_text(value: Any, limit: int, fallback: str = "") -> str:
    """Nettoie un texte de manifeste."""

    text = re.sub(r"[|\r\n]+", " ", str(value or fallback))
    return re.sub(r"\s+", " ", text).strip()[:limit]


def normalise_package_id(value: Any) -> str:
    """Valide un identifiant de paquet."""

    text = str(value or "")
    if not PACKAGE_ID_RE.fullmatch(text):
        raise ValueError("Identifiant de paquet invalide")
    return text


def _safe_service_id(value: Any) -> str:
    """Valide un identifiant de service."""

    text = str(value or "")
    if not SERVICE_ID_RE.fullmatch(text):
        raise ValueError("Identifiant de service invalide")
    return text


def _string_list(
    value: Any,
    allowed: set[str] | frozenset[str],
    label: str,
    maximum: int,
) -> list[str]:
    """Valide une liste de valeurs bornée."""

    if not isinstance(value, list) or len(value) > maximum:
        raise ValueError(f"Liste {label} invalide")
    result = []
    for item in value:
        text = str(item or "")
        if text not in allowed or text in result:
            raise ValueError(f"Valeur {label} invalide")
        result.append(text)
    return result


def _validate_default(field: dict[str, Any]) -> object:
    """Valide la valeur initiale d’un réglage."""

    field_type = field["type"]
    raw = field.get("default", "")
    if field_type in {"text", "secret"}:
        text = str(raw or "")[: field["max_length"]]
        if field_type == "secret" and text:
            raise ValueError("Un secret ne peut pas avoir de valeur initiale")
        return text
    if field_type == "integer":
        try:
            return int(raw or 0)
        except (TypeError, ValueError) as exc:
            raise ValueError("Valeur entière invalide") from exc
    if field_type == "boolean":
        if isinstance(raw, bool):
            return raw
        if str(raw).lower() in {"1", "true", "yes", "on"}:
            return True
        if str(raw).lower() in {"0", "false", "no", "off", ""}:
            return False
        raise ValueError("Valeur booléenne invalide")
    choices = field["choices"]
    text = str(raw or choices[0])
    if text not in choices:
        raise ValueError("Choix initial invalide")
    return text


def _configuration(raw: Any) -> list[dict[str, Any]]:
    """Valide les réglages déclarés."""

    if raw is None:
        return []
    if not isinstance(raw, list) or len(raw) > 16:
        raise ValueError("Configuration de paquet invalide")
    result = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ValueError("Réglage de paquet invalide")
        key = str(item.get("key") or "").strip().lower()
        if not CONFIG_KEY_RE.fullmatch(key) or key in seen:
            raise ValueError("Clé de réglage invalide")
        seen.add(key)
        field_type = str(item.get("type") or "text").strip().lower()
        if field_type not in _ALLOWED_CONFIG_TYPES:
            raise ValueError("Type de réglage invalide")
        choices = item.get("choices", [])
        if field_type == "choice":
            if not isinstance(choices, list) or not 1 <= len(choices) <= 32:
                raise ValueError("Choix de réglage invalides")
            choices = [_clean_text(value, 64) for value in choices]
            if any(not value for value in choices) or len(set(choices)) != len(choices):
                raise ValueError("Choix de réglage invalides")
        else:
            choices = []
        field = {
            "key": key,
            "type": field_type,
            "required": bool(item.get("required", False)),
            "label": _clean_text(item.get("label"), 64, key),
            "label_fr": _clean_text(item.get("label_fr"), 64, item.get("label") or key),
            "choices": choices,
            "max_length": max(1, min(int(item.get("max_length", 256)), 2048)),
        }
        field["default"] = _validate_default({**field, "default": item.get("default", "")})
        result.append(field)
    return result


def _dependencies(raw: Any, package_id: str) -> list[dict[str, str]]:
    """Valide les dépendances déclarées."""

    if raw is None:
        return []
    if not isinstance(raw, list) or len(raw) > 8:
        raise ValueError("Dépendances de paquet invalides")
    result = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ValueError("Dépendance de paquet invalide")
        dependency_id = normalise_package_id(item.get("id"))
        if dependency_id == package_id or dependency_id in seen:
            raise ValueError("Dépendance de paquet dupliquée")
        seen.add(dependency_id)
        minimum = normalise_semver(item.get("min_version", "0.0.0"))
        maximum_raw = str(item.get("max_version") or "").strip()
        maximum = normalise_semver(maximum_raw) if maximum_raw else ""
        if maximum and parse_semver(maximum) < parse_semver(minimum):
            raise ValueError("Plage de dépendance invalide")
        result.append({"id": dependency_id, "min_version": minimum, "max_version": maximum})
    return result


def _headers(raw: Any) -> dict[str, str]:
    """Valide les en-têtes HTTP déclarés."""

    if raw is None:
        return {}
    if not isinstance(raw, dict) or len(raw) > 12:
        raise ValueError("En-têtes HTTP invalides")
    result = {}
    for name, value in raw.items():
        key = str(name or "").strip()
        text = str(value or "")[:2048]
        if not HEADER_NAME_RE.fullmatch(key) or "\r" in text or "\n" in text:
            raise ValueError("En-tête HTTP invalide")
        if key.lower() in {"host", "content-length", "connection", "transfer-encoding"}:
            raise ValueError("En-tête HTTP interdit")
        result[key] = text
    return result


def _request(raw: Any, ui_mode: str, config_keys: set[str]) -> dict[str, Any]:
    """Valide une requête HTTP déclarative."""

    if not isinstance(raw, dict):
        raise ValueError("Requête de service absente")
    method = str(raw.get("method") or "GET").strip().upper()
    if method not in _ALLOWED_METHODS:
        raise ValueError("Méthode HTTP interdite")
    url = str(raw.get("url") or "").strip()[:2000]
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("URL de service invalide")
    if parsed.username or parsed.password:
        raise ValueError("Identifiants interdits dans l URL")
    body = str(raw.get("body") or "")[:8192]
    headers = _headers(raw.get("headers"))
    templates = [url, body, *headers.values()]
    used_keys = {key for template in templates for key in CONFIG_TOKEN_RE.findall(template)}
    if not used_keys.issubset(config_keys):
        raise ValueError("Réglage inconnu dans une requête")
    if ui_mode == "text" and not any("{input}" in template for template in templates):
        raise ValueError("Une saisie doit utiliser le marqueur input")
    response_path = str(raw.get("response_path") or "").strip()[:200]
    return {
        "method": method,
        "url": url,
        "headers": headers,
        "body": body,
        "response_path": response_path,
        "result_title": _clean_text(raw.get("result_title"), 80, "Result"),
        "result_title_fr": _clean_text(
            raw.get("result_title_fr"),
            80,
            raw.get("result_title") or "Résultat",
        ),
        "max_bytes": max(1024, min(int(raw.get("max_bytes", 262144)), 524288)),
    }



def _services(raw: Any, config_keys: set[str]) -> list[dict[str, Any]]:
    """Valide les services déclarés."""

    if not isinstance(raw, list) or not 1 <= len(raw) <= MAX_PACKAGE_SERVICES:
        raise ValueError("Un paquet doit déclarer entre un et dix services")
    result = []
    seen: set[str] = set()
    for item in raw:
        if not isinstance(item, dict):
            raise ValueError("Service de paquet invalide")
        service_id = _safe_service_id(item.get("id"))
        if service_id in seen:
            raise ValueError("Identifiant de service dupliqué")
        seen.add(service_id)
        category = str(item.get("category") or "extensions").strip().lower()
        if category not in _ALLOWED_CATEGORIES:
            raise ValueError("Catégorie de service invalide")
        permissions = _string_list(
            item.get("permissions", ["network.http"]),
            _ALLOWED_PERMISSIONS,
            "permission",
            16,
        )
        platforms = _string_list(
            item.get("platforms", ["desktop"]),
            _ALLOWED_PLATFORMS,
            "plateforme",
            4,
        )
        ui = item.get("ui", {})
        if not isinstance(ui, dict):
            raise ValueError("Interface de service invalide")
        ui_mode = str(ui.get("mode") or "text").strip().lower()
        if ui_mode not in _ALLOWED_UI_MODES:
            raise ValueError("Mode d interface invalide")
        request = _request(item.get("request"), ui_mode, config_keys)
        result.append(
            {
                "id": service_id,
                "title": _clean_text(item.get("title"), 80, service_id),
                "title_fr": _clean_text(
                    item.get("title_fr"),
                    80,
                    item.get("title") or service_id,
                ),
                "category": category,
                "permissions": sorted(permissions),
                "timeout": max(1.0, min(float(item.get("timeout", 10)), 60.0)),
                "platforms": platforms,
                "ui": {
                    "mode": ui_mode,
                    "prompt": _clean_text(ui.get("prompt"), 48, "Value"),
                    "prompt_fr": _clean_text(
                        ui.get("prompt_fr"),
                        48,
                        ui.get("prompt") or "Valeur",
                    ),
                    "initial": _clean_text(ui.get("initial"), 80),
                },
                "request": request,
            }
        )
    return result


def validate_manifest(raw: dict[str, Any]) -> dict[str, Any]:
    """Valide puis normalise un manifeste externe."""

    if not isinstance(raw, dict):
        raise ValueError("Le manifeste doit être un objet JSON")
    try:
        schema = int(raw.get("schema", 0))
    except (TypeError, ValueError) as exc:
        raise ValueError("Schéma de paquet invalide") from exc
    if schema not in SUPPORTED_PACKAGE_SCHEMAS:
        raise ValueError("Schéma de paquet non pris en charge")
    package_id = normalise_package_id(raw.get("id"))
    version = normalise_semver(raw.get("version"))
    minimum = normalise_semver(raw.get("min_bridge", "0.1.0"))
    maximum_raw = str(raw.get("max_bridge") or "").strip()
    maximum = normalise_semver(maximum_raw) if maximum_raw else ""
    if maximum and parse_semver(maximum) < parse_semver(minimum):
        raise ValueError("Plage de passerelle invalide")
    platforms = _string_list(
        raw.get("platforms", ["desktop"]),
        _ALLOWED_PLATFORMS,
        "plateforme",
        4,
    )
    configuration = _configuration(raw.get("configuration"))
    config_keys = {item["key"] for item in configuration}
    services_raw = raw.get("services")
    manifest = {
        "schema": PACKAGE_SCHEMA,
        "id": package_id,
        "version": version,
        "title": _clean_text(raw.get("title"), 80, package_id),
        "title_fr": _clean_text(raw.get("title_fr"), 80, raw.get("title") or package_id),
        "description": _clean_text(raw.get("description"), 240),
        "description_fr": _clean_text(
            raw.get("description_fr"),
            240,
            raw.get("description") or "",
        ),
        "author": _clean_text(raw.get("author"), 80),
        "license": _clean_text(raw.get("license"), 40),
        "homepage": str(raw.get("homepage") or "").strip()[:1000],
        "min_bridge": minimum,
        "max_bridge": maximum,
        "platforms": platforms,
        "dependencies": _dependencies(raw.get("dependencies"), package_id),
        "configuration": configuration,
        "services": _services(services_raw, config_keys),
    }
    homepage = manifest["homepage"]
    if homepage:
        parsed = urllib.parse.urlparse(homepage)
        if parsed.scheme != "https" or not parsed.hostname:
            raise ValueError("Page du paquet invalide")
    return manifest


def bridge_compatibility_issues(manifest: dict[str, Any]) -> tuple[str, ...]:
    """Liste les problèmes de compatibilité du bridge."""

    if version_is_compatible(__version__, manifest["min_bridge"], manifest["max_bridge"]):
        return ()
    maximum = manifest["max_bridge"] or "sans limite"
    return (f"Bridge {__version__} hors plage {manifest['min_bridge']} à {maximum}",)


def dependency_issues(
    manifest: dict[str, Any],
    installed_versions: dict[str, str],
) -> tuple[str, ...]:
    """Liste les dépendances absentes ou incompatibles."""

    issues = []
    for dependency in manifest["dependencies"]:
        current = installed_versions.get(dependency["id"])
        if current is None:
            issues.append(f"Dépendance absente {dependency['id']}")
            continue
        if not version_is_compatible(
            current,
            dependency["min_version"],
            dependency["max_version"],
        ):
            issues.append(f"Dépendance incompatible {dependency['id']} {current}")
    return tuple(issues)


def required_configuration_issues(
    manifest: dict[str, Any],
    values: dict[str, object],
) -> tuple[str, ...]:
    """Liste les réglages obligatoires manquants."""

    issues = []
    for field in manifest["configuration"]:
        value = values.get(field["key"], field["default"])
        if field["required"] and (value is None or value == ""):
            issues.append(f"Réglage requis absent {field['key']}")
    return tuple(issues)


def configuration_defaults(manifest: dict[str, Any]) -> dict[str, object]:
    """Construit les réglages initiaux d’un paquet."""

    return {field["key"]: field["default"] for field in manifest["configuration"]}


def configuration_keys(manifest: dict[str, Any]) -> frozenset[str]:
    """Liste les clés déclarées par le paquet."""

    return frozenset(field["key"] for field in manifest["configuration"])


def redact_configuration(
    manifest: dict[str, Any],
    values: dict[str, object],
) -> dict[str, object]:
    """Masque les valeurs secrètes."""

    result = {}
    for field in manifest["configuration"]:
        value = values.get(field["key"], field["default"])
        result[field["key"]] = "********" if field["type"] == "secret" and value else value
    return result


def iter_service_ids(manifest: dict[str, Any]) -> Iterable[str]:
    """Parcourt les identifiants et leurs alias."""

    for service in manifest["services"]:
        yield service["id"]
