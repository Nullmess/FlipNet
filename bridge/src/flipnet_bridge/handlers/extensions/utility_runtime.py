"""Gère les applications en ligne."""

from __future__ import annotations

import ast
import ipaddress
import json
import math
import os
import re
import socket
import unicodedata
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError, available_timezones

from ...core.config import BridgeConfig
from ...core.localization import tr
from ...core.user_agent import BRIDGE_USER_AGENT as USER_AGENT

APP_ID_RE = re.compile(r"[a-z0-9_]{1,24}")
MAX_RESPONSE_BYTES = 262_144


class OnlineAppError(RuntimeError):
    """Signale une erreur d’application en ligne."""


# Décrit une application en ligne.
@dataclass(frozen=True, slots=True)
class OnlineAppSpec:
    app_id: str
    title: str
    prompt: str
    initial: str
    mode: str = "input"
    title_fr: str = ""
    prompt_fr: str = ""
    origin: str = "bundled"
    extension_group: str = "utilities"


# Construit les applications intégrées.
def _builtin_apps() -> tuple[OnlineAppSpec, ...]:
    return (
        OnlineAppSpec(
            "translate",
            "Translate",
            "Languages",
            "French English",
            "translate_flow",
            "Traduction",
            "Langues",
        ),
        OnlineAppSpec(
            "calculator",
            "Calculator",
            "Expression",
            "(12 + 8) / 4",
            "input",
            "Calculatrice",
            "Expression",
        ),
        OnlineAppSpec(
            "convert",
            "Unit converter",
            "Value and units",
            "10 km to mi",
            "input",
            "Conversion d unites",
            "Valeur et unites",
        ),
        OnlineAppSpec(
            "currency",
            "Currency",
            "Amount and currencies",
            "10 EUR to USD",
            "input",
            "Devises",
            "Montant et devises",
        ),
        OnlineAppSpec(
            "timezone",
            "World time",
            "City or timezone",
            "Paris",
            "search",
            "Heure mondiale",
            "Ville ou fuseau",
        ),
    )


# Nettoie un champ de configuration.
def _clean_field(value: Any, limit: int) -> str:
    text = re.sub(r"[|\r\n]+", " ", str(value or ""))
    return re.sub(r"\s+", " ", text).strip()[:limit]


# Charge les applications configurées.
def _custom_specs(config: BridgeConfig) -> list[OnlineAppSpec]:
    specs: list[OnlineAppSpec] = []
    for item in config.online_apps:
        app_id = str(item.get("id", "")).strip().lower()
        if not APP_ID_RE.fullmatch(app_id):
            continue
        mode = str(item.get("mode", "input")).strip().lower()
        if mode not in {"input", "run"}:
            mode = "input"
        specs.append(
            OnlineAppSpec(
                app_id=app_id,
                title=_clean_field(
                    item.get("title", app_id.replace("_", " ").title()),
                    48,
                ),
                prompt=_clean_field(
                    item.get("prompt", "Value"),
                    48,
                ),
                initial=_clean_field(
                    item.get("initial", ""),
                    80,
                ),
                mode=mode,
                title_fr=_clean_field(item.get("title_fr", ""), 48),
                prompt_fr=_clean_field(item.get("prompt_fr", ""), 48),
                origin="configured",
            )
        )
    return specs


def catalog(
    config: BridgeConfig,
    include_plugins: bool = True,
) -> tuple[OnlineAppSpec, ...]:
    """Liste les applications en ligne."""

    items = list(_builtin_apps())
    if config.ai_endpoint and config.ai_model:
        items.append(
            OnlineAppSpec(
                "ai_answer",
                "Ask AI",
                "Question",
                "Explain DNS simply",
                "input",
                "Interroger l IA",
                "Question",
            )
        )
    known = {item.app_id for item in items}
    if include_plugins:
        try:
            from ...core.platform_paths import default_plugins_path
            from ...plugins.manifest import validate_manifest
            from ...plugins.package import _read_json, list_packages

            for package in list_packages(default_plugins_path()):
                if not package.enabled or package.issues:
                    continue
                manifest = validate_manifest(_read_json(package.path / "manifest.json"))
                for service in manifest["services"]:
                    if service["id"] in known:
                        continue
                    ui = service["ui"]
                    items.append(
                        OnlineAppSpec(
                            service["id"],
                            service["title"],
                            ui["prompt"],
                            ui["initial"],
                            "run" if ui["mode"] == "direct" else "input",
                            service["title_fr"],
                            ui["prompt_fr"],
                            "installed",
                            "plugins",
                        )
                    )
                    known.add(service["id"])
                    if len(items) >= 16:
                        break
                if len(items) >= 16:
                    break
        except (OSError, ValueError):
            pass
    for item in _custom_specs(config):
        if item.app_id not in known:
            known.add(item.app_id)
            items.append(item)
        if len(items) >= 16:
            break
    return tuple(items[:16])



_TRANSLATION_LANGUAGES: tuple[tuple[str, str, str], ...] = (
    ("ar", "Arabic", "Arabe"),
    ("zh", "Chinese", "Chinois"),
    ("nl", "Dutch", "Neerlandais"),
    ("en", "English", "Anglais"),
    ("fr", "French", "Francais"),
    ("de", "German", "Allemand"),
    ("el", "Greek", "Grec"),
    ("hi", "Hindi", "Hindi"),
    ("it", "Italian", "Italien"),
    ("ja", "Japanese", "Japonais"),
    ("ko", "Korean", "Coreen"),
    ("pl", "Polish", "Polonais"),
    ("pt", "Portuguese", "Portugais"),
    ("ro", "Romanian", "Roumain"),
    ("ru", "Russian", "Russe"),
    ("es", "Spanish", "Espagnol"),
    ("sv", "Swedish", "Suedois"),
    ("tr", "Turkish", "Turc"),
    ("uk", "Ukrainian", "Ukrainien"),
)


_COMMON_TIMEZONES: tuple[str, ...] = (
    "Europe/Paris",
    "Europe/London",
    "Europe/Berlin",
    "America/New_York",
    "America/Los_Angeles",
    "Asia/Tokyo",
    "Asia/Shanghai",
    "Australia/Sydney",
)


# Construit la clé de recherche.
def _search_key(value: str) -> str:
    folded = unicodedata.normalize("NFKD", value)
    ascii_text = folded.encode("ascii", "ignore").decode("ascii").lower()
    return re.sub(r"[^a-z0-9]+", " ", ascii_text).strip()


# Calcule la pertinence d’un champ.
def _field_score(query: str, value: str) -> int:
    if not query:
        return 1
    q = _search_key(query)
    hay = _search_key(value)
    if not q or not hay:
        return 0
    if q == hay:
        return 1000
    if hay.startswith(q):
        return 700
    if any(part.startswith(q) for part in hay.split()):
        return 600
    if q in hay:
        return 450
    tokens = q.split()
    if tokens and all(any(word.startswith(token) for word in hay.split()) for token in tokens):
        return 350 + len(tokens) * 10
    return 0


# Construit les choix de langue.
def _language_choices(language: str, *, exclude: str = "") -> list[tuple[str, str]]:
    choices: list[tuple[str, str]] = []
    for code, english, _french in _TRANSLATION_LANGUAGES:
        if code == exclude:
            continue
        choices.append((code, english))
    return choices


# Construit les couples de traduction.
def _translation_choices(query: str, language: str) -> list[tuple[str, str]]:
    q = _search_key(query)
    languages = list(_TRANSLATION_LANGUAGES)
    names = {code: english for code, english, _french in languages}
    if not q:
        defaults = (("fr", "en"), ("en", "fr"), ("es", "fr"), ("fr", "es"))
        connector = " to "
        return [
            (f"{source}>{target}", f"{names[source]}{connector}{names[target]}")
            for source, target in defaults
        ]
    scored: list[tuple[int, str, str]] = []
    q_tokens = q.split()
    connector = " to "
    for source_code, source_en, source_fr in languages:
        for target_code, target_en, target_fr in languages:
            if source_code == target_code:
                continue
            source_name = source_en
            target_name = target_en
            label = f"{source_name}{connector}{target_name}"
            aliases = f"{source_code} {source_en} {source_fr} {target_code} {target_en} {target_fr}"
            score = _field_score(q, aliases)
            if len(q_tokens) >= 2:
                source_score = _field_score(q_tokens[0], f"{source_code} {source_en} {source_fr}")
                target_query = " ".join(q_tokens[1:])
                target_score = _field_score(target_query, f"{target_code} {target_en} {target_fr}")
                if source_score and target_score:
                    score += 1200 + source_score + target_score
            if score:
                if {source_code, target_code} == {"fr", "en"}:
                    score += 25
                scored.append((score, f"{source_code}>{target_code}", label))
    scored.sort(key=lambda item: (-item[0], item[2]))
    return [(value, label) for _, value, label in scored[:8]]


# Construit les choix de fuseau horaire.
def _timezone_choices(query: str) -> list[tuple[str, str]]:
    q = query.strip()
    zones = sorted(available_timezones())
    if not q:
        zones = list(_COMMON_TIMEZONES)
    scored: list[tuple[int, str, str]] = []
    for zone in zones:
        city = zone.rsplit("/", 1)[-1].replace("_", " ")
        label = f"{city} - {zone}"
        score = _field_score(q, f"{city} {zone}") if q else 1
        if score:
            if zone in _COMMON_TIMEZONES:
                score += 20
            scored.append((score, zone, label))
    scored.sort(key=lambda item: (-item[0], item[2]))
    return [(zone, label) for _, zone, label in scored[:8]]


def search_payload(argument: str, config: BridgeConfig) -> str:
    """Construit les choix d’une application."""

    language = config.language[:2].lower()
    app_id, query = _parse_run_argument(argument)
    if app_id == "timezone":
        choices = _timezone_choices(query)
    elif app_id == "translate_source":
        choices = _language_choices(language)
    elif app_id == "translate_target":
        choices = _language_choices(language, exclude=query.strip().lower())
    elif app_id == "translate":
        choices = _translation_choices(query, language)
    else:
        raise OnlineAppError(
            tr(
                language,
                "Search unavailable for this app",
                "Recherche indisponible pour cette application",
            )
        )
    if not choices:
        raise OnlineAppError(tr(language, "No matching result", "Aucun resultat correspondant"))
    lines = ["FNSEL1", app_id]
    used = sum(len(line.encode("utf-8")) + 1 for line in lines)
    for value, label in choices[:20]:
        line = f"{_clean_field(value, 48)}|{_clean_field(label, 72)}"
        size = len(line.encode("utf-8")) + 1
        if used + size > 1000:
            break
        lines.append(line)
        used += size
    return "\n".join(lines)


# Valide l’URL publique.
def _validate_public_url(value: str) -> str:
    parsed = urllib.parse.urlparse(value.strip())
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise OnlineAppError("URL HTTP ou HTTPS invalide")
    if parsed.username or parsed.password:
        raise OnlineAppError("Identifiants interdits dans l URL")
    hostname = parsed.hostname.rstrip(".").lower()
    if hostname == "localhost" or hostname.endswith((".local", ".localhost")):
        raise OnlineAppError("Adresse locale interdite")
    try:
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        addresses = {item[4][0] for item in socket.getaddrinfo(hostname, port)}
    except OSError as exc:
        raise OnlineAppError(f"Domaine introuvable: {exc}") from exc
    if not addresses:
        raise OnlineAppError("Domaine introuvable")
    for address in addresses:
        try:
            ip = ipaddress.ip_address(address.split("%", 1)[0])
        except ValueError as exc:
            raise OnlineAppError("Adresse IP invalide") from exc
        if not ip.is_global:
            raise OnlineAppError("Adresse privee ou locale interdite")
    return urllib.parse.urlunparse(parsed._replace(fragment=""))


# Bloque les redirections vers les réseaux privés.
class _PublicRedirectHandler(urllib.request.HTTPRedirectHandler):
    # Valide chaque redirection HTTP.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return super().redirect_request(req, fp, code, msg, headers, _validate_public_url(newurl))


# Télécharge puis analyse une réponse JSON.
def _request_json(
    url: str,
    timeout: float,
    *,
    headers: dict[str, str] | None = None,
    payload: dict[str, Any] | None = None,
) -> Any:
    safe_url = _validate_public_url(url)
    request_headers = {
        "User-Agent": USER_AGENT,
        "Accept": "application/json",
    }
    if headers:
        request_headers.update(headers)
    data = None
    method = "GET"
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
        method = "POST"
    request = urllib.request.Request(safe_url, data=data, headers=request_headers, method=method)
    opener = urllib.request.build_opener(_PublicRedirectHandler())
    try:
        with opener.open(request, timeout=timeout) as response:
            if response.status < 200 or response.status >= 300:
                raise OnlineAppError(f"HTTP {response.status}")
            raw = response.read(MAX_RESPONSE_BYTES + 1)
            if len(raw) > MAX_RESPONSE_BYTES:
                raise OnlineAppError("Reponse trop volumineuse")
    except OnlineAppError:
        raise
    except Exception as exc:
        raise OnlineAppError(f"Erreur reseau: {exc}") from exc
    try:
        return json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise OnlineAppError("Reponse JSON invalide") from exc


# Sépare l’application et sa saisie.
def _parse_run_argument(argument: str) -> tuple[str, str]:
    app_id, separator, value = argument.partition("|")
    app_id = app_id.strip().lower()
    if not APP_ID_RE.fullmatch(app_id):
        raise OnlineAppError("Application invalide")
    if not separator:
        value = ""
    return app_id, value.strip()


# Analyse un nombre décimal.
def _number(value: str) -> float:
    try:
        number = float(value.replace(",", "."))
    except ValueError as exc:
        raise OnlineAppError("Nombre invalide") from exc
    if not math.isfinite(number) or abs(number) > 1e18:
        raise OnlineAppError("Nombre hors limite")
    return number


# Formate un nombre sans bruit inutile.
def _format_number(value: float) -> str:
    if not math.isfinite(value) or abs(value) > 1e18:
        raise OnlineAppError("Resultat hors limite")
    if abs(value - round(value)) < 1e-12:
        return str(int(round(value)))
    return f"{value:.10g}"


_ALLOWED_BINOPS = {
    ast.Add: lambda left, right: left + right,
    ast.Sub: lambda left, right: left - right,
    ast.Mult: lambda left, right: left * right,
    ast.Div: lambda left, right: left / right,
    ast.FloorDiv: lambda left, right: left // right,
    ast.Mod: lambda left, right: left % right,
    ast.Pow: lambda left, right: left**right,
}


# Évalue une expression arithmétique sûre.
def _eval_expression(node: ast.AST, depth: int = 0) -> float:
    if depth > 12:
        raise OnlineAppError("Expression trop complexe")
    if isinstance(node, ast.Expression):
        return _eval_expression(node.body, depth + 1)
    if isinstance(node, ast.Constant) and isinstance(node.value, int | float):
        return _number(str(node.value))
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.UAdd | ast.USub):
        value = _eval_expression(node.operand, depth + 1)
        return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp) and type(node.op) in _ALLOWED_BINOPS:
        left = _eval_expression(node.left, depth + 1)
        right = _eval_expression(node.right, depth + 1)
        if isinstance(node.op, ast.Pow) and (abs(right) > 12 or abs(left) > 1e9):
            raise OnlineAppError("Puissance hors limite")
        try:
            return _ALLOWED_BINOPS[type(node.op)](left, right)
        except (ArithmeticError, OverflowError) as exc:
            raise OnlineAppError("Calcul impossible") from exc
    raise OnlineAppError("Expression non autorisee")


# Exécute la calculatrice locale.
def _calculator(value: str) -> tuple[str, str]:
    if not value or len(value) > 96:
        raise OnlineAppError("Expression invalide")
    try:
        tree = ast.parse(value, mode="eval")
    except SyntaxError as exc:
        raise OnlineAppError("Expression invalide") from exc
    result = _eval_expression(tree)
    return "Calculator", f"{value}\n= {_format_number(result)}"


_UNIT_FACTORS: dict[str, tuple[str, float]] = {
    "mm": ("length", 0.001),
    "cm": ("length", 0.01),
    "m": ("length", 1.0),
    "km": ("length", 1000.0),
    "in": ("length", 0.0254),
    "ft": ("length", 0.3048),
    "yd": ("length", 0.9144),
    "mi": ("length", 1609.344),
    "mg": ("mass", 0.001),
    "g": ("mass", 1.0),
    "kg": ("mass", 1000.0),
    "oz": ("mass", 28.349523125),
    "lb": ("mass", 453.59237),
    "ms": ("time", 0.001),
    "s": ("time", 1.0),
    "min": ("time", 60.0),
    "h": ("time", 3600.0),
    "d": ("time", 86400.0),
    "b": ("data", 1.0),
    "kb": ("data", 1000.0),
    "mb": ("data", 1_000_000.0),
    "gb": ("data", 1_000_000_000.0),
    "kib": ("data", 1024.0),
    "mib": ("data", 1_048_576.0),
    "gib": ("data", 1_073_741_824.0),
}


# Convertit une température.
def _temperature(value: float, source: str, target: str) -> float:
    source = source.lower()
    target = target.lower()
    if source == "c":
        kelvin = value + 273.15
    elif source == "f":
        kelvin = (value - 32.0) * 5.0 / 9.0 + 273.15
    elif source == "k":
        kelvin = value
    else:
        raise OnlineAppError("Unite de temperature invalide")
    if target == "c":
        return kelvin - 273.15
    if target == "f":
        return (kelvin - 273.15) * 9.0 / 5.0 + 32.0
    if target == "k":
        return kelvin
    raise OnlineAppError("Unite de temperature invalide")


# Convertit une valeur entre deux unités.
def _convert(value: str, config: BridgeConfig) -> tuple[str, str]:
    match = re.fullmatch(
        r"\s*([-+]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s*([A-Za-z]+)\s+(?:to|in|vers)\s+([A-Za-z]+)\s*",
        value,
        re.IGNORECASE,
    )
    if not match:
        raise OnlineAppError("Format: 10 km to mi")
    amount = _number(match.group(1))
    source = match.group(2).lower()
    target = match.group(3).lower()
    if source in {"c", "f", "k"} or target in {"c", "f", "k"}:
        result = _temperature(amount, source, target)
    else:
        source_info = _UNIT_FACTORS.get(source)
        target_info = _UNIT_FACTORS.get(target)
        if not source_info or not target_info or source_info[0] != target_info[0]:
            raise OnlineAppError("Unites incompatibles")
        result = amount * source_info[1] / target_info[1]
    body = f"{_format_number(amount)} {source}\n= {_format_number(result)} {target}"
    return tr(config.language[:2].lower(), "Unit converter", "Convertisseur"), body


# Traduit un texte.
def _translation(value: str, config: BridgeConfig) -> tuple[str, str]:
    match = re.fullmatch(r"\s*([A-Za-z-]{2,12})>([A-Za-z-]{2,12})\s+(.+)\s*", value, re.DOTALL)
    if not match:
        raise OnlineAppError("Format: fr>en Bonjour")
    source, target, text = match.groups()
    if len(text.encode("utf-8")) > 500:
        raise OnlineAppError("Texte trop long")
    query = urllib.parse.urlencode({"q": text, "langpair": f"{source}|{target}"})
    payload = _request_json(
        f"https://api.mymemory.translated.net/get?{query}",
        config.request_timeout_seconds,
    )
    translated = str((payload.get("responseData") or {}).get("translatedText") or "").strip()
    if not translated:
        raise OnlineAppError("Traduction indisponible")
    return f"{source.lower()} > {target.lower()}", translated


# Convertit une devise.
def _currency(value: str, config: BridgeConfig) -> tuple[str, str]:
    match = re.fullmatch(
        r"\s*([-+]?(?:\d+(?:[.,]\d*)?|[.,]\d+))\s+([A-Za-z]{3})\s+(?:to|in|vers)\s+([A-Za-z]{3})\s*",
        value,
        re.IGNORECASE,
    )
    if not match:
        raise OnlineAppError("Format: 10 EUR to USD")
    amount = _number(match.group(1))
    base = match.group(2).upper()
    quote = match.group(3).upper()
    if base == quote:
        rate = 1.0
        date = datetime.now(UTC).date().isoformat()
    else:
        payload = _request_json(
            f"https://api.frankfurter.dev/v2/rate/{base}/{quote}",
            config.request_timeout_seconds,
        )
        try:
            rate = float(payload["rate"])
        except (KeyError, TypeError, ValueError) as exc:
            raise OnlineAppError("Taux de change indisponible") from exc
        date = str(payload.get("date") or "latest")
    result = amount * rate
    return tr(config.language[:2].lower(), "Currency", "Devises"), (
        f"{_format_number(amount)} {base}\n= {_format_number(result)} {quote}"
        f"\n{tr(config.language[:2].lower(), 'Rate', 'Taux')}: {_format_number(rate)}\nDate: {date}"
    )


# Convertit une heure entre deux fuseaux.
def _timezone(value: str) -> tuple[str, str]:
    name = value.strip() or "UTC"
    try:
        zone = ZoneInfo(name)
    except ZoneInfoNotFoundError as exc:
        raise OnlineAppError("Timezone inconnue") from exc
    now = datetime.now(zone)
    offset = now.utcoffset()
    total_minutes = int(offset.total_seconds() // 60) if offset else 0
    sign = "+" if total_minutes >= 0 else "-"
    hours, minutes = divmod(abs(total_minutes), 60)
    return name, f"{now:%Y-%m-%d}\n{now:%H:%M:%S}\nUTC{sign}{hours:02d}:{minutes:02d}"


# Découpe un chemin JSON.
def _path_tokens(path: str) -> list[str | int]:
    tokens: list[str | int] = []
    for name, index in re.findall(r"([A-Za-z0-9_-]+)|\[(\d+)\]", path):
        tokens.append(name if name else int(index))
    return tokens


# Lit une valeur dans une réponse JSON.
def _extract_path(payload: Any, path: str) -> Any:
    current = payload
    for token in _path_tokens(path):
        try:
            current = current[token]
        except (KeyError, IndexError, TypeError) as exc:
            raise OnlineAppError("Champ JSON introuvable") from exc
    return current


# Formate une valeur JSON pour le Flipper.
def _display_json(value: Any) -> str:
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return "null"
    if isinstance(value, int | float):
        return str(value)
    return json.dumps(value, ensure_ascii=False, indent=2)[:6000]


# Exécute une application configurée.
def _custom_app(app_id: str, value: str, config: BridgeConfig) -> tuple[str, str]:
    item = next((entry for entry in config.online_apps if entry.get("id") == app_id), None)
    if item is None:
        raise OnlineAppError("Application inconnue")
    template = str(item.get("url") or "")
    if "{input}" in template:
        url = template.replace("{input}", urllib.parse.quote(value, safe=""))
    else:
        url = template
    headers: dict[str, str] = {}
    raw_headers = item.get("headers")
    if isinstance(raw_headers, dict):
        for name, raw_value in list(raw_headers.items())[:12]:
            header_name = str(name).strip()
            header_value = str(raw_value).strip()
            if header_value.startswith("env:"):
                header_value = os.environ.get(header_value[4:].strip(), "")
            if re.fullmatch(r"[A-Za-z0-9-]{1,64}", header_name) and header_value:
                headers[header_name] = header_value[:512]
    payload = _request_json(url, config.request_timeout_seconds, headers=headers)
    response_path = str(item.get("response_path") or "").strip()
    result = _extract_path(payload, response_path) if response_path else payload
    title = str(item.get("result_title") or item.get("title") or app_id).strip()
    title_path = str(item.get("title_path") or "").strip()
    if title_path:
        title = _display_json(_extract_path(payload, title_path))
    body = _display_json(result)
    if not body:
        raise OnlineAppError("Reponse vide")
    return _clean_field(title, 64), body[:6000]


# Interroge le service de discussion configuré.
def _ai_answer(value: str, config: BridgeConfig) -> tuple[str, str]:
    prompt = value.strip()
    if not prompt:
        raise OnlineAppError("Question vide")
    if not config.ai_endpoint or not config.ai_model:
        raise OnlineAppError("AI non configuree")
    headers: dict[str, str] = {}
    api_key = os.environ.get(config.ai_api_key_env, "") if config.ai_api_key_env else ""
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    payload = _request_json(
        config.ai_endpoint,
        min(config.feature_timeout_cap_seconds, 60.0),
        headers=headers,
        payload={
            "model": config.ai_model,
            "messages": [{"role": "user", "content": prompt}],
            "max_tokens": 220,
            "temperature": 0.2,
        },
    )
    candidates = (
        "choices[0].message.content",
        "output_text",
        "response",
        "text",
    )
    for path in candidates:
        try:
            answer = _display_json(_extract_path(payload, path)).strip()
        except OnlineAppError:
            continue
        if answer:
            return tr(config.language[:2].lower(), "AI answer", "Reponse IA"), answer[:6000]
    raise OnlineAppError("Reponse AI incompatible")


def run(argument: str, config: BridgeConfig) -> tuple[str, str]:
    """Exécute l’application en ligne demandée."""

    app_id, value = _parse_run_argument(argument)
    if app_id == "translate":
        return _translation(value, config)
    if app_id == "calculator":
        return _calculator(value)
    if app_id == "convert":
        return _convert(value, config)
    if app_id == "currency":
        return _currency(value, config)
    if app_id == "timezone":
        return _timezone(value)
    if app_id == "ai_answer":
        return _ai_answer(value, config)
    try:
        return _custom_app(app_id, value, config)
    except OnlineAppError as exc:
        if "Application inconnue" not in str(exc) and "Unknown" not in str(exc):
            raise
        from . import plugins

        result = plugins.dispatch(app_id, value, config, None, protocol_version=2)
        return result.title, result.body
