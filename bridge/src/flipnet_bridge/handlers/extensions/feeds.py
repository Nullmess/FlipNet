"""Gère les flux et les articles."""

from __future__ import annotations

from typing import TYPE_CHECKING

from .. import shared as api

if TYPE_CHECKING:
    pass


FEED_DIGESTS: dict[str, tuple[str, tuple[str, ...]]] = {
    "fr_news": (
        "Actualites FR",
        (
            "fr_lemonde",
            "fr_franceinfo",
            "fr_france24",
            "fr_rfi",
            "fr_lefigaro",
            "fr_liberation",
            "fr_20minutes",
            "fr_bfmtv",
        ),
    ),
    "fr_tech": (
        "Numerique FR",
        (
            "fr_lemonde_pixels",
            "fr_numerama",
            "fr_01net",
            "fr_clubic",
            "fr_nextink",
            "fr_jdg",
            "fr_frandroid",
            "fr_lesnumeriques",
            "fr_korben",
            "fr_linuxfr",
        ),
    ),
    "fr_cyber": ("Cyber FR", ("fr_certfr", "fr_nextink", "fr_korben", "fr_numerama")),
    "fr_systems": ("Systemes FR", ("fr_linuxfr", "fr_nextink", "fr_korben", "fr_01net")),
    "fr_all": (
        "Mix FR",
        (
            "fr_lemonde",
            "fr_franceinfo",
            "fr_numerama",
            "fr_01net",
            "fr_linuxfr",
            "fr_certfr",
            "fr_france24",
            "fr_nextink",
        ),
    ),
    "en_news": ("World News EN", ("en_bbc", "en_guardian", "en_nyt", "en_aljazeera", "en_npr")),
    "en_tech": (
        "Tech News EN",
        ("en_ars", "en_verge", "en_techcrunch", "en_wired", "en_engadget", "en_hn"),
    ),
    "en_cyber": (
        "Cyber EN",
        (
            "en_cisa",
            "en_bleeping",
            "en_krebs",
            "en_thn",
            "en_google_sec",
            "en_ms_sec",
            "en_schneier",
        ),
    ),
    "en_systems": (
        "Systems EN",
        ("en_redhat", "en_lwn", "en_ubuntu", "en_docker", "en_kubernetes", "en_cloudflare"),
    ),
    "en_all": (
        "Mix EN",
        (
            "en_bbc",
            "en_guardian",
            "en_ars",
            "en_verge",
            "en_bleeping",
            "en_lwn",
            "en_cloudflare",
            "en_cisa",
        ),
    ),
}


# Regroupe les flux intégrés et personnalisés.
def _feed_catalog(config: api.BridgeConfig) -> dict[str, dict[str, str]]:
    catalog = {key: dict(value) for key, value in api.BUILTIN_FEEDS.items()}
    for index, feed in enumerate(config.rss_feeds):
        if not isinstance(feed, dict) or not feed.get("url"):
            continue
        feed_id = str(feed.get("id") or f"custom_{index}").strip().lower()
        catalog[feed_id] = {
            "id": feed_id,
            "language": str(feed.get("language", "custom")),
            "category": str(feed.get("category", "custom")),
            "title": str(feed.get("title", f"Custom {index + 1}")),
            "url": str(feed["url"]),
        }
    return catalog


# Nettoie le texte d’un flux.
def _clean_feed_text(value: str, limit: int) -> str:
    cleaned = api._normalise(api._clean_html(value), True)
    cleaned = api.re.sub("\\s+", " ", cleaned).strip()
    cleaned = cleaned.replace("|", "/").replace("=", "-")
    return cleaned[:limit]


# Analyse les articles RSS ou Atom.
def _rss_entries(raw: bytes, limit: int = 6) -> list[api.FeedEntry]:
    try:
        root = api.ET.fromstring(raw)
    except api.ET.ParseError as exc:
        raise api.ServiceError("Flux RSS invalide") from exc
    entries: list[api.FeedEntry] = []
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1].lower()
        if tag not in {"item", "entry"}:
            continue
        title = ""
        summary = ""
        link = ""
        for child in list(element):
            child_tag = child.tag.rsplit("}", 1)[-1].lower()
            if child_tag == "title" and (not title):
                title = _clean_feed_text("".join(child.itertext()), 150)
            elif child_tag in {"description", "summary", "content", "encoded"} and (not summary):
                summary = _clean_feed_text("".join(child.itertext()), 620)
            elif child_tag == "link" and (not link):
                link = (child.text or child.attrib.get("href") or "").strip()
        if not title:
            continue
        entries.append(api.FeedEntry(title=title, summary=summary, link=link[:320]))
        if len(entries) >= limit:
            break
    return entries


# Construit les cartes d’articles.
def _feed_cards(title: str, items: list[tuple[str, int, api.FeedEntry]]) -> api.ServiceResult:
    if not items:
        raise api.ServiceError("Aucun titre dans le flux")
    lines = ["FNFEED1", f"Title={_clean_feed_text(title, 42)}", f"Count={len(items)}"]
    for source_id, source_index, entry in items[:6]:
        lines.append(f"Item={source_id}|{source_index}|{entry.title}")
    return api.ServiceResult(title, "\n".join(lines))


# Liste les titres d’un flux.
def headlines(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    source_id = argument.strip().lower() or config.default_rss_source
    catalog = _feed_catalog(config)
    feed = catalog.get(source_id)
    if feed is None:
        raise api.ServiceError(f"Source RSS inconnue: {source_id}")
    raw = api._request_bytes(str(feed["url"]), config.request_timeout_seconds)
    entries = _rss_entries(raw)
    items = [(source_id, index + 1, entry) for index, entry in enumerate(entries)]
    return _feed_cards(str(feed["title"]), items)


# Sélectionne les articles du résumé.
def _digest_entries(
    source_id: str, feed: dict[str, str], config: api.BridgeConfig
) -> tuple[str, list[api.FeedEntry]]:
    raw = api._request_bytes(str(feed["url"]), config.request_timeout_seconds)
    return (source_id, _rss_entries(raw, limit=3))


# Construit le résumé des flux.
def feed_digest(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    digest_id = argument.strip().lower()
    digest = FEED_DIGESTS.get(digest_id)
    if digest is None:
        raise api.ServiceError(f"Digest inconnu: {digest_id}")
    title, source_ids = digest
    catalog = _feed_catalog(config)
    items: list[tuple[str, int, api.FeedEntry]] = []
    failures = 0
    available = [
        (source_id, catalog[source_id]) for source_id in source_ids if source_id in catalog
    ]
    results: dict[str, list[api.FeedEntry]] = {}
    workers = min(6, len(available))
    if workers:
        with api.concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
            futures = {
                executor.submit(_digest_entries, source_id, feed, config): source_id
                for source_id, feed in available
            }
            for future in api.concurrent.futures.as_completed(futures):
                source_id = futures[future]
                try:
                    _, entries = future.result()
                    results[source_id] = entries
                except api.ServiceError:
                    failures += 1
    for source_id in source_ids:
        for index, entry in enumerate(results.get(source_id, []), start=1):
            items.append((source_id, index, entry))
    if not items:
        raise api.ServiceError("Aucune source du digest ne repond")
    source_order = {source_id: index for index, source_id in enumerate(source_ids)}
    items.sort(key=lambda item: (item[1], source_order[item[0]]))
    result = _feed_cards(title, items[:6])
    if failures:
        return api.ServiceResult(result.title, result.body + f"\nPartial={failures}")
    return result


# Charge un article de flux.
def feed_article(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    fields = argument.split("|", 1)
    if len(fields) != 2:
        raise api.ServiceError(api.tr(config.language[:2], "Invalid article", "Article invalide"))
    source_id = fields[0].strip().lower()
    try:
        article_index = int(fields[1])
    except ValueError as exc:
        raise api.ServiceError("Numero d'article invalide") from exc
    if article_index < 1 or article_index > 20:
        raise api.ServiceError("Article introuvable")
    catalog = _feed_catalog(config)
    feed = catalog.get(source_id)
    if feed is None:
        raise api.ServiceError(f"Source RSS inconnue: {source_id}")
    raw = api._request_bytes(str(feed["url"]), config.request_timeout_seconds)
    entries = _rss_entries(raw, limit=article_index)
    if len(entries) < article_index:
        raise api.ServiceError("Article introuvable")
    entry = entries[article_index - 1]
    body_parts = [f"Source: {feed['title']}"]
    if entry.summary:
        body_parts.extend(["", entry.summary])
    if entry.link:
        body_parts.extend(["", entry.link])
    return api.ServiceResult(entry.title, "\n".join(body_parts))
