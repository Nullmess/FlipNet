"""Construit le catalogue FNCAP2."""

from __future__ import annotations

import hashlib
from collections.abc import Iterable
from dataclasses import dataclass

from ..registry.api import FeatureManifest
from .config import BridgeConfig

MAX_PAYLOAD_BYTES = 1000
PROTOCOL_PREFIX = "FNCAP2"


@dataclass(frozen=True, slots=True)
class CapabilityPageRequest:
    """Décrit une demande de page FNCAP2."""

    page: int


# Encode un champ du protocole.
def encode_field(value: object) -> str:
    text = str(value or "")
    return (
        text.replace("%", "%25")
        .replace("|", "%7C")
        .replace(",", "%2C")
        .replace("\r", "%0D")
        .replace("\n", "%0A")
    )


# Assemble une ligne bornée.
def encode_line(fields: Iterable[object]) -> str:
    return "|".join(encode_field(field) for field in fields)


# Analyse la page demandée.
def parse_page_request(argument: str) -> CapabilityPageRequest:
    raw = str(argument or "").strip()
    if not raw.startswith("2|"):
        raise ValueError("Demande FNCAP2 invalide")
    page_text = raw[2:]
    if not page_text.isdecimal():
        raise ValueError("Page FNCAP2 invalide")
    page = int(page_text)
    if page > 255:
        raise ValueError("Page FNCAP2 invalide")
    return CapabilityPageRequest(page)


# Construit une ligne de fonctionnalité.
def feature_line(manifest: FeatureManifest) -> str:
    return encode_line(
        (
            "F",
            manifest.service_id,
            manifest.domain,
            manifest.group,
            manifest.kind,
            manifest.category,
            manifest.input_mode,
            manifest.title,
            manifest.title_fr or manifest.title,
            manifest.prompt_en,
            manifest.prompt_fr or manifest.prompt_en,
            manifest.initial,
            ",".join(sorted(manifest.permissions)),
            ",".join(manifest.platforms),
            manifest.origin,
            manifest.extension_group,
            "1" if manifest.visible else "0",
            manifest.next_service,
        )
    )


# Retourne le service utilisé par une application.
def app_service(item) -> str:
    mode = str(item.mode)
    if mode in {"search", "pick_input", "translate_flow"}:
        return "app_search"
    return "app_run"


# Construit une ligne d’application en ligne.
def app_line(item) -> str:
    mode = str(item.mode)
    service = app_service(item)
    return encode_line(
        (
            "A",
            item.app_id,
            service,
            mode,
            item.title,
            item.title_fr or item.title,
            item.prompt,
            item.prompt_fr or item.prompt,
            item.initial,
            item.origin,
            item.extension_group,
            "1",
        )
    )


# Construit une ligne d application pour un paquet externe.
def plugin_app_line(manifest: FeatureManifest) -> str:
    mode = "run" if manifest.input_mode == "direct" else "input"
    return encode_line(
        (
            "A",
            manifest.service_id,
            "app_run",
            mode,
            manifest.title,
            manifest.title_fr or manifest.title,
            manifest.prompt_en,
            manifest.prompt_fr or manifest.prompt_en,
            manifest.initial,
            "installed",
            "plugins",
            "1",
        )
    )


# Répartit les lignes selon la limite filaire.
def paginate(platform: str, lines: Iterable[str]) -> tuple[str, ...]:
    records = tuple(lines)
    digest = hashlib.sha256("\n".join(records).encode("utf-8")).hexdigest()[:12].upper()
    pages: list[list[str]] = [[]]
    for record in records:
        if len(record.encode("utf-8")) + 1 > MAX_PAYLOAD_BYTES - 40:
            raise ValueError("Entree FNCAP2 trop volumineuse")
        trial = pages[-1] + [record]
        header = f"{PROTOCOL_PREFIX}\n{platform}|0|255|{digest}"
        size = len((header + "\n" + "\n".join(trial)).encode("utf-8"))
        if size > MAX_PAYLOAD_BYTES and pages[-1]:
            pages.append([record])
        else:
            pages[-1] = trial
    total = len(pages)
    payloads = []
    for page_index, page in enumerate(pages):
        header = f"{PROTOCOL_PREFIX}\n{platform}|{page_index}|{total}|{digest}"
        payload = header + ("\n" + "\n".join(page) if page else "")
        if len(payload.encode("utf-8")) > MAX_PAYLOAD_BYTES:
            raise ValueError("Page FNCAP2 trop volumineuse")
        payloads.append(payload)
    return tuple(payloads)


# Construit toutes les pages FNCAP2.
def catalog_pages(
    config: BridgeConfig,
    manifests: Iterable[FeatureManifest],
    platform: str,
) -> tuple[str, ...]:
    from ..handlers.extensions.utility_runtime import catalog

    available = tuple(
        manifest
        for manifest in manifests
        if platform in manifest.platforms
        and (manifest.visible or manifest.service_id == "capabilities")
    )
    service_ids = {manifest.service_id for manifest in available}
    feature_records = [feature_line(manifest) for manifest in available]
    bundled_apps = [
        app_line(item)
        for item in catalog(config, include_plugins=False)
        if app_service(item) in service_ids and item.origin == "bundled"
    ]
    plugin_apps = [
        plugin_app_line(manifest)
        for manifest in available
        if manifest.origin == "installed" and "app_run" in service_ids
    ]
    configured_apps = [
        app_line(item)
        for item in catalog(config, include_plugins=False)
        if app_service(item) in service_ids and item.origin != "bundled"
    ]
    app_records = (bundled_apps + plugin_apps + configured_apps)[:16]
    return paginate(platform, (*feature_records, *app_records))


# Retourne la page FNCAP2 demandée.
def catalog_payload(
    config: BridgeConfig,
    manifests: Iterable[FeatureManifest],
    platform: str,
    page: int,
) -> str:
    pages = catalog_pages(config, manifests, platform)
    if page >= len(pages):
        raise ValueError("Page FNCAP2 absente")
    return pages[page]
