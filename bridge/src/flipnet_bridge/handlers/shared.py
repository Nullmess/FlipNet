"""Regroupe les types et outils partagés des gestionnaires."""

from __future__ import annotations

import concurrent.futures
import ctypes
import hashlib
import html
import ipaddress
import json
import os
import platform
import re
import shutil
import socket
import ssl
import subprocess
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import UTC, datetime
from html.parser import HTMLParser
from pathlib import Path
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from ..core.state import StateStore

from .extensions import utility_runtime
from ..core.config import BridgeConfig
from ..core.localization import tr
from ..core.user_agent import BRIDGE_USER_AGENT as USER_AGENT
from ..core.user_agent import WEB_BROWSER_USER_AGENT


class ServiceError(RuntimeError):
    """Signale une erreur destinée à l’utilisateur."""


# Représente un résultat de fonctionnalité.
@dataclass(frozen=True, slots=True)
class ServiceResult:
    title: str
    body: str


# Décrit un article de flux Android.
@dataclass(frozen=True, slots=True)
class FeedEntry:
    title: str
    summary: str
    link: str


# Décrit un résultat Web.
@dataclass(frozen=True, slots=True)
class WebEntry:
    token: str
    title: str
    source: str
    snippet: str
    url: str


BUILTIN_FEEDS: dict[str, dict[str, str]] = {
    "fr_certfr": {
        "id": "fr_certfr",
        "language": "fr",
        "category": "cyber",
        "title": "CERT-FR",
        "url": "https://www.cert.ssi.gouv.fr/feed/",
    },
    "fr_lemonde": {
        "id": "fr_lemonde",
        "language": "fr",
        "category": "news",
        "title": "Le Monde",
        "url": "https://www.lemonde.fr/rss/une.xml",
    },
    "fr_lemonde_pixels": {
        "id": "fr_lemonde_pixels",
        "language": "fr",
        "category": "tech",
        "title": "Le Monde Pixels",
        "url": "https://www.lemonde.fr/pixels/rss_full.xml",
    },
    "fr_franceinfo": {
        "id": "fr_franceinfo",
        "language": "fr",
        "category": "news",
        "title": "franceinfo",
        "url": "https://www.francetvinfo.fr/titres.rss",
    },
    "fr_france24": {
        "id": "fr_france24",
        "language": "fr",
        "category": "news",
        "title": "France 24",
        "url": "https://www.france24.com/fr/rss",
    },
    "fr_rfi": {
        "id": "fr_rfi",
        "language": "fr",
        "category": "news",
        "title": "RFI",
        "url": "https://www.rfi.fr/fr/rss",
    },
    "fr_lefigaro": {
        "id": "fr_lefigaro",
        "language": "fr",
        "category": "news",
        "title": "Le Figaro",
        "url": "https://www.lefigaro.fr/rss/figaro_actualites.xml",
    },
    "fr_liberation": {
        "id": "fr_liberation",
        "language": "fr",
        "category": "news",
        "title": "Liberation",
        "url": "https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml",
    },
    "fr_20minutes": {
        "id": "fr_20minutes",
        "language": "fr",
        "category": "news",
        "title": "20 Minutes",
        "url": "https://www.20minutes.fr/feeds/rss-une.xml",
    },
    "fr_bfmtv": {
        "id": "fr_bfmtv",
        "language": "fr",
        "category": "news",
        "title": "BFMTV",
        "url": "https://www.bfmtv.com/rss/news-24-7/",
    },
    "fr_numerama": {
        "id": "fr_numerama",
        "language": "fr",
        "category": "tech",
        "title": "Numerama",
        "url": "https://www.numerama.com/feed/",
    },
    "fr_01net": {
        "id": "fr_01net",
        "language": "fr",
        "category": "tech",
        "title": "01net",
        "url": "https://www.01net.com/actualites/feed/",
    },
    "fr_clubic": {
        "id": "fr_clubic",
        "language": "fr",
        "category": "tech",
        "title": "Clubic",
        "url": "https://www.clubic.com/feed/news.rss",
    },
    "fr_nextink": {
        "id": "fr_nextink",
        "language": "fr",
        "category": "tech",
        "title": "Next.ink",
        "url": "https://next.ink/feed/",
    },
    "fr_jdg": {
        "id": "fr_jdg",
        "language": "fr",
        "category": "tech",
        "title": "Journal du Geek",
        "url": "https://www.journaldugeek.com/feed/",
    },
    "fr_frandroid": {
        "id": "fr_frandroid",
        "language": "fr",
        "category": "tech",
        "title": "Frandroid",
        "url": "https://www.frandroid.com/feed",
    },
    "fr_lesnumeriques": {
        "id": "fr_lesnumeriques",
        "language": "fr",
        "category": "tech",
        "title": "Les Numeriques",
        "url": "https://www.lesnumeriques.com/rss.xml",
    },
    "fr_korben": {
        "id": "fr_korben",
        "language": "fr",
        "category": "tech",
        "title": "Korben",
        "url": "https://korben.info/feed",
    },
    "fr_linuxfr": {
        "id": "fr_linuxfr",
        "language": "fr",
        "category": "systems",
        "title": "LinuxFr.org",
        "url": "https://linuxfr.org/news.atom",
    },
    "en_bbc": {
        "id": "en_bbc",
        "language": "en",
        "category": "news",
        "title": "BBC News",
        "url": "https://feeds.bbci.co.uk/news/rss.xml",
    },
    "en_guardian": {
        "id": "en_guardian",
        "language": "en",
        "category": "news",
        "title": "The Guardian",
        "url": "https://www.theguardian.com/world/rss",
    },
    "en_nyt": {
        "id": "en_nyt",
        "language": "en",
        "category": "news",
        "title": "New York Times",
        "url": "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
    },
    "en_aljazeera": {
        "id": "en_aljazeera",
        "language": "en",
        "category": "news",
        "title": "Al Jazeera",
        "url": "https://www.aljazeera.com/xml/rss/all.xml",
    },
    "en_npr": {
        "id": "en_npr",
        "language": "en",
        "category": "news",
        "title": "NPR News",
        "url": "https://feeds.npr.org/1001/rss.xml",
    },
    "en_ars": {
        "id": "en_ars",
        "language": "en",
        "category": "tech",
        "title": "Ars Technica",
        "url": "https://feeds.arstechnica.com/arstechnica/index",
    },
    "en_verge": {
        "id": "en_verge",
        "language": "en",
        "category": "tech",
        "title": "The Verge",
        "url": "https://www.theverge.com/rss/index.xml",
    },
    "en_techcrunch": {
        "id": "en_techcrunch",
        "language": "en",
        "category": "tech",
        "title": "TechCrunch",
        "url": "https://techcrunch.com/feed/",
    },
    "en_wired": {
        "id": "en_wired",
        "language": "en",
        "category": "tech",
        "title": "Wired",
        "url": "https://www.wired.com/feed/rss",
    },
    "en_engadget": {
        "id": "en_engadget",
        "language": "en",
        "category": "tech",
        "title": "Engadget",
        "url": "https://www.engadget.com/rss.xml",
    },
    "en_hn": {
        "id": "en_hn",
        "language": "en",
        "category": "tech",
        "title": "Hacker News",
        "url": "https://hnrss.org/frontpage",
    },
    "en_redhat": {
        "id": "en_redhat",
        "language": "en",
        "category": "systems",
        "title": "Red Hat Blog",
        "url": "https://www.redhat.com/en/rss/blog",
    },
    "en_lwn": {
        "id": "en_lwn",
        "language": "en",
        "category": "systems",
        "title": "LWN.net",
        "url": "https://lwn.net/headlines/rss",
    },
    "en_ubuntu": {
        "id": "en_ubuntu",
        "language": "en",
        "category": "systems",
        "title": "Ubuntu Blog",
        "url": "https://ubuntu.com/blog/feed",
    },
    "en_docker": {
        "id": "en_docker",
        "language": "en",
        "category": "systems",
        "title": "Docker Blog",
        "url": "https://www.docker.com/blog/feed/",
    },
    "en_kubernetes": {
        "id": "en_kubernetes",
        "language": "en",
        "category": "systems",
        "title": "Kubernetes Blog",
        "url": "https://kubernetes.io/feed.xml",
    },
    "en_cloudflare": {
        "id": "en_cloudflare",
        "language": "en",
        "category": "systems",
        "title": "Cloudflare Blog",
        "url": "https://blog.cloudflare.com/rss/",
    },
    "en_cisa": {
        "id": "en_cisa",
        "language": "en",
        "category": "cyber",
        "title": "CISA Advisories",
        "url": "https://www.cisa.gov/cybersecurity-advisories/all.xml",
    },
    "en_bleeping": {
        "id": "en_bleeping",
        "language": "en",
        "category": "cyber",
        "title": "BleepingComputer",
        "url": "https://www.bleepingcomputer.com/feed/",
    },
    "en_krebs": {
        "id": "en_krebs",
        "language": "en",
        "category": "cyber",
        "title": "Krebs on Security",
        "url": "https://krebsonsecurity.com/feed/",
    },
    "en_thn": {
        "id": "en_thn",
        "language": "en",
        "category": "cyber",
        "title": "The Hacker News",
        "url": "https://feeds.feedburner.com/TheHackersNews",
    },
    "en_google_sec": {
        "id": "en_google_sec",
        "language": "en",
        "category": "cyber",
        "title": "Google Security Blog",
        "url": "https://security.googleblog.com/feeds/posts/default",
    },
    "en_ms_sec": {
        "id": "en_ms_sec",
        "language": "en",
        "category": "cyber",
        "title": "Microsoft Security",
        "url": "https://www.microsoft.com/en-us/security/blog/feed/",
    },
    "en_schneier": {
        "id": "en_schneier",
        "language": "en",
        "category": "cyber",
        "title": "Schneier on Security",
        "url": "https://www.schneier.com/feed/atom/",
    },
}

DISCOVERY_PORTS: tuple[int, ...] = (22, 53, 80, 88, 135, 389, 443, 445, 3389, 8006, 8123, 9090)


# Télécharge une réponse bornée.
def _request_bytes(
    url: str,
    timeout: float,
    headers: dict[str, str] | None = None,
    max_bytes: int = 1_000_000,
) -> bytes:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ServiceError("URL HTTP ou HTTPS invalide")

    request_headers = {
        "User-Agent": USER_AGENT,
        "Accept": "application/json, application/xml, text/xml, text/plain, */*",
    }
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(url, headers=request_headers, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            if response.status < 200 or response.status >= 300:
                raise ServiceError(f"HTTP {response.status}")
            payload = response.read(max_bytes + 1)
            if len(payload) > max_bytes:
                raise ServiceError("Reponse trop volumineuse")
            return payload
    except ServiceError:
        raise
    except Exception as exc:
        raise ServiceError(f"Erreur reseau: {exc}") from exc


# Télécharge puis analyse une réponse JSON.
def _request_json(url: str, timeout: float, headers: dict[str, str] | None = None) -> Any:
    try:
        return json.loads(_request_bytes(url, timeout, headers).decode("utf-8"))
    except ServiceError:
        raise
    except Exception as exc:
        raise ServiceError("Reponse JSON invalide") from exc


# Retire les balises HTML.
def _clean_html(value: str) -> str:
    value = re.sub(r"<[^>]+>", " ", value)
    value = html.unescape(value)
    return re.sub(r"\s+", " ", value).strip()


ASCII_REPLACEMENTS = str.maketrans(
    {
        "\u00a0": " ",
        "\u00ad": "",
        "\u200b": "",
        "\ufeff": "",
        "Æ": "AE",
        "æ": "ae",
        "Œ": "OE",
        "œ": "oe",
        "ẞ": "SS",
        "ß": "ss",
        "Ø": "O",
        "ø": "o",
        "Ł": "L",
        "ł": "l",
        "Đ": "D",
        "đ": "d",
        "Ð": "D",
        "ð": "d",
        "Þ": "TH",
        "þ": "th",
        "‘": "'",
        "’": "'",
        "‚": "'",
        "‛": "'",
        "“": '"',
        "”": '"',
        "„": '"',
        "‟": '"',
        "‐": "-",
        "‑": "-",
        "‒": "-",
        "–": "-",
        "—": "-",
        "−": "-",
        "…": "...",
        "•": "-",
        "·": "-",
        "∙": "-",
        "◦": "-",
        "→": "->",
        "←": "<-",
        "↔": "<->",
        "⇒": "=>",
        "⇐": "<=",
        "×": "x",
        "÷": "/",
        "±": "+/-",
        "≈": "~",
        "≠": "!=",
        "≤": "<=",
        "≥": ">=",
        "°": " deg",
        "€": " EUR",
        "£": " GBP",
        "¥": " JPY",
        "¢": " cent",
        "©": "(C)",
        "®": "(R)",
        "™": "(TM)",
        "½": "1/2",
        "¼": "1/4",
        "¾": "3/4",
        "ⓘ": "(i)",
        "ℹ": "(i)",
        "▶": "[play]",
        "►": "[play]",
        "🔊": "[audio]",
        "🔉": "[audio]",
        "🔈": "[audio]",
        "🎧": "[audio]",
        "ˈ": "",
        "ˌ": "",
        "ː": ":",
        "ˑ": ":",
        "ʼ": "'",
        "ʰ": "h",
        "ʲ": "y",
        "ʷ": "w",
        "ʱ": "h",
        "ʴ": "r",
        "ɐ": "a",
        "ɑ": "a",
        "ɒ": "o",
        "ɓ": "b",
        "ʙ": "b",
        "β": "b",
        "ɔ": "o",
        "ɕ": "sh",
        "ç": "c",
        "ɗ": "d",
        "ɖ": "d",
        "ɘ": "e",
        "ə": "e",
        "ɚ": "er",
        "ɛ": "e",
        "ɜ": "e",
        "ɝ": "er",
        "ɞ": "o",
        "ɟ": "j",
        "ʄ": "j",
        "ɡ": "g",
        "ɢ": "g",
        "ʛ": "g",
        "ɦ": "h",
        "ɧ": "h",
        "ħ": "h",
        "ɥ": "w",
        "ʜ": "h",
        "ɨ": "i",
        "ɪ": "i",
        "ɫ": "l",
        "ɬ": "l",
        "ɭ": "l",
        "ɮ": "l",
        "ʟ": "l",
        "ɱ": "m",
        "ɯ": "u",
        "ɰ": "w",
        "ŋ": "ng",
        "ɳ": "n",
        "ɲ": "ny",
        "ɴ": "n",
        "ɵ": "o",
        "ɸ": "f",
        "ɹ": "r",
        "ɻ": "r",
        "ɽ": "r",
        "ɾ": "r",
        "ʀ": "r",
        "ʁ": "r",
        "ɺ": "r",
        "ɼ": "r",
        "ʂ": "sh",
        "ʃ": "sh",
        "ʈ": "t",
        "θ": "th",
        "ʉ": "u",
        "ʊ": "u",
        "ʋ": "v",
        "ⱱ": "v",
        "ʌ": "u",
        "ɣ": "g",
        "ɤ": "o",
        "χ": "kh",
        "ʎ": "y",
        "ʏ": "y",
        "ʐ": "zh",
        "ʑ": "zh",
        "ʒ": "zh",
        "ʔ": "'",
        "ʕ": "'",
        "ʡ": "'",
    }
)


# Convertit le texte pour le Flipper.
def _ascii_text(value: str) -> str:
    mapped = value.translate(ASCII_REPLACEMENTS)
    decomposed = unicodedata.normalize("NFKD", mapped)
    output: list[str] = []
    for character in decomposed:
        if ord(character) < 128:
            output.append(character)
        elif unicodedata.combining(character):
            continue
        elif unicodedata.category(character).startswith("N"):
            try:
                output.append(str(unicodedata.digit(character)))
            except (TypeError, ValueError):
                output.append(" ")
        else:
            # Retire les signes Unicode restants.
            output.append(" ")
    lines = [re.sub(r"[ \t\f\v]+", " ", line).strip() for line in "".join(output).split("\n")]
    return "\n".join(lines).strip()


# Normalise le texte affiché.
def _normalise(value: str, ascii_only: bool) -> str:
    value = value.replace("\r", "").strip()
    if ascii_only:
        return _ascii_text(value)
    return value


# Réduit un texte sans couper l’UTF-8.
def _short(value: Any, limit: int = 180) -> str:
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    if len(text) <= limit:
        return text
    return text[: max(0, limit - 3)].rstrip() + "..."


# Exige un stockage persistant.
def _need_state(state: StateStore | None) -> StateStore:
    if state is None:
        raise ServiceError("Stockage indisponible")
    return state
