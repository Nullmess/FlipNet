"""Valide les catalogues de paquets externes."""

from __future__ import annotations

import json
import urllib.request
from pathlib import Path
from typing import Any

from ..core.user_agent import BRIDGE_USER_AGENT
from ..handlers.extensions.web import _PublicRedirectHandler, _validate_public_web_url
from .manifest import normalise_package_id, normalise_semver, parse_semver
from .package import list_packages


def _catalog_item(raw: dict[str, Any]) -> dict[str, str]:
    """Valide une entrée de catalogue distant."""

    package_id = normalise_package_id(raw.get("id"))
    version = normalise_semver(raw.get("version"))
    url = _validate_public_web_url(str(raw.get("url") or "").strip())
    if not url.endswith(".flipnet-plugin"):
        raise ValueError("URL de paquet invalide")
    checksum = str(raw.get("sha256") or "").strip().lower()
    if len(checksum) != 64 or any(char not in "0123456789abcdef" for char in checksum):
        raise ValueError("Empreinte de catalogue invalide")
    return {
        "id": package_id,
        "version": version,
        "title": str(raw.get("title") or package_id).strip()[:80],
        "title_fr": str(raw.get("title_fr") or raw.get("title") or package_id).strip()[:80],
        "url": url,
        "sha256": checksum,
    }


def fetch_catalog(url: str, timeout: float = 10.0) -> list[dict[str, str]]:
    """Télécharge puis valide un catalogue distant."""

    safe_url = _validate_public_web_url(url)
    request = urllib.request.Request(
        safe_url,
        headers={"User-Agent": BRIDGE_USER_AGENT, "Accept": "application/json"},
    )
    opener = urllib.request.build_opener(_PublicRedirectHandler())
    with opener.open(request, timeout=timeout) as response:
        if response.status < 200 or response.status >= 300:
            raise ValueError(f"Catalogue HTTP {response.status}")
        raw = response.read(500_001)
        if len(raw) > 500_000:
            raise ValueError("Catalogue distant trop volumineux")
        payload = json.loads(raw.decode("utf-8"))
    if not isinstance(payload, dict) or int(payload.get("schema", 0)) != 1:
        raise ValueError("Catalogue distant incompatible")
    items = payload.get("packages", [])
    if not isinstance(items, list) or len(items) > 100:
        raise ValueError("Liste du catalogue invalide")
    result = []
    seen: set[str] = set()
    for raw in items:
        if not isinstance(raw, dict):
            raise ValueError("Entrée du catalogue invalide")
        item = _catalog_item(raw)
        if item["id"] in seen:
            raise ValueError("Paquet dupliqué dans le catalogue")
        seen.add(item["id"])
        result.append(item)
    return result


def available_updates(
    url: str,
    plugins_dir: Path,
    timeout: float = 10.0,
) -> tuple[dict[str, str], ...]:
    """Liste les versions plus récentes du catalogue."""

    installed = {item.package_id: item.version for item in list_packages(plugins_dir)}
    updates = []
    for item in fetch_catalog(url, timeout):
        current = installed.get(item["id"])
        if current and parse_semver(item["version"]) > parse_semver(current):
            updates.append({**item, "installed_version": current})
    return tuple(updates)
