"""Charge les services des paquets externes."""

from __future__ import annotations

import json
import re
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from ..core.config import BridgeConfig
from ..core.user_agent import BRIDGE_USER_AGENT
from ..handlers import shared as services
from ..handlers.extensions.web import _PublicRedirectHandler, _validate_public_web_url
from ..registry.api import Feature, FeatureManifest
from .manifest import CONFIG_TOKEN_RE, validate_manifest
from .package import _read_json, list_packages, read_configuration

_INPUT_TOKEN = "{input}"


def _extract_path(payload: Any, path: str) -> Any:
    """Lit une valeur dans une réponse JSON."""

    current = payload
    if not path:
        return current
    for part in path.split("."):
        if isinstance(current, dict):
            current = current.get(part)
        elif isinstance(current, list) and part.isdecimal():
            index = int(part)
            current = current[index] if index < len(current) else None
        else:
            return None
    return current


def _plain_value(value: object) -> str:
    """Nettoie une valeur injectée dans une requête."""

    return re.sub(r"[\r\n]+", " ", str(value or ""))


def _render_plain(
    template: str,
    argument: str,
    configuration: dict[str, object],
) -> str:
    """Remplace les marqueurs dans un texte brut."""

    result = template.replace(_INPUT_TOKEN, _plain_value(argument))
    return CONFIG_TOKEN_RE.sub(
        lambda match: _plain_value(configuration.get(match.group(1), "")),
        result,
    )


def _render_url(
    template: str,
    argument: str,
    configuration: dict[str, object],
) -> str:
    """Remplace les marqueurs dans une URL."""

    result = template.replace(
        _INPUT_TOKEN,
        urllib.parse.quote(argument.strip(), safe=""),
    )
    return CONFIG_TOKEN_RE.sub(
        lambda match: urllib.parse.quote(
            str(configuration.get(match.group(1), "")),
            safe="",
        ),
        result,
    )


def _request_json(
    request_spec: dict[str, Any],
    argument: str,
    configuration: dict[str, object],
    timeout: float,
) -> Any:
    """Exécute une requête JSON bornée."""

    url = _validate_public_web_url(_render_url(request_spec["url"], argument, configuration))
    headers = {
        "User-Agent": BRIDGE_USER_AGENT,
        "Accept": "application/json",
    }
    for name, value in request_spec["headers"].items():
        headers[name] = _render_plain(value, argument, configuration)
    body_text = _render_plain(request_spec["body"], argument, configuration)
    data = body_text.encode("utf-8") if request_spec["method"] == "POST" else None
    request = urllib.request.Request(
        url,
        data=data,
        headers=headers,
        method=request_spec["method"],
    )
    opener = urllib.request.build_opener(_PublicRedirectHandler())
    try:
        with opener.open(request, timeout=timeout) as response:
            if response.status < 200 or response.status >= 300:
                raise services.ServiceError(f"HTTP {response.status}")
            payload = response.read(request_spec["max_bytes"] + 1)
            if len(payload) > request_spec["max_bytes"]:
                raise services.ServiceError("Réponse du module trop volumineuse")
    except services.ServiceError:
        raise
    except Exception as exc:
        raise services.ServiceError(f"Erreur réseau du module: {exc}") from exc
    try:
        return json.loads(payload.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise services.ServiceError("Réponse JSON du module invalide") from exc


def _handler_for(
    package_id: str,
    plugins_dir: Path,
    item: dict[str, Any],
):
    """Construit le gestionnaire déclaratif d’un service."""

    def run(
        argument: str,
        config: BridgeConfig,
        state: object,
    ) -> services.ServiceResult:
        """Exécute le service externe déclaré."""

        del state
        values = read_configuration(package_id, plugins_dir)
        request_spec = item["request"]
        payload = _request_json(
            request_spec,
            argument,
            values,
            min(item["timeout"], config.feature_timeout_cap_seconds),
        )
        value = _extract_path(payload, request_spec["response_path"])
        if value is None:
            raise services.ServiceError("Chemin de réponse du module absent")
        if isinstance(value, (dict, list)):
            body = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        else:
            body = str(value)
        title = (
            request_spec["result_title_fr"]
            if config.language[:2].lower() == "fr"
            else request_spec["result_title"]
        )
        return services.ServiceResult(title, body[:3500])

    return run


def load_plugins(plugins_dir: Path) -> tuple[Feature, ...]:
    """Charge les services externes actifs et compatibles."""

    plugins = []
    for package in list_packages(plugins_dir):
        if not package.enabled or package.issues:
            continue
        manifest = validate_manifest(_read_json(package.path / "manifest.json"))
        for item in manifest["services"]:
            ui = item["ui"]
            plugins.append(
                Feature(
                    FeatureManifest(
                        service_id=item["id"],
                        title=item["title"],
                        title_fr=item["title_fr"],
                        category=item["category"],
                        permissions=frozenset(item["permissions"]),
                        timeout_seconds=item["timeout"],
                        origin="installed",
                        extension_group="plugins",
                        platforms=tuple(item["platforms"]),
                        visible=True,
                        kind="plugin",
                        domain="extensions",
                        group=package.package_id,
                        input_mode=ui["mode"],
                        prompt_en=ui["prompt"],
                        prompt_fr=ui["prompt_fr"],
                        initial=ui["initial"],
                    ),
                    _handler_for(package.package_id, plugins_dir, item),
                )
            )
    return tuple(plugins)
