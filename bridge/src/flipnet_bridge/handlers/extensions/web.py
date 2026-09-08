"""Gère la recherche et la lecture Web."""

from __future__ import annotations

from typing import TYPE_CHECKING

from .. import shared as api

if TYPE_CHECKING:
    from ...core.state import StateStore


# Extrait les résultats de recherche DuckDuckGo.
class _DuckDuckGoParser(api.HTMLParser):
    # Initialise l état de lecture HTML.
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.items: list[dict[str, str]] = []
        self._link = ""
        self._title: list[str] = []
        self._snippet: list[str] = []
        self._capture_title = False
        self._capture_snippet = False

    # Traite une balise HTML ouvrante.
    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = {key.lower(): value or "" for key, value in attrs}
        classes = set(attributes.get("class", "").split())
        href = attributes.get("href", "")
        rel = set(attributes.get("rel", "").split())
        result_link = bool({"result__a", "result-link"} & classes)
        lite_link = "nofollow" in rel and (
            "uddg=" in href or href.startswith(("http://", "https://"))
        )
        if tag == "a" and (result_link or lite_link):
            self._link = href
            self._title = []
            self._capture_title = True
        elif {"result__snippet", "result-snippet", "result-snippet__text"} & classes:
            self._snippet = []
            self._capture_snippet = True

    # Collecte le texte d’une page HTML.
    def handle_data(self, data: str) -> None:
        if self._capture_title:
            self._title.append(data)
        if self._capture_snippet:
            self._snippet.append(data)

    # Traite une balise HTML fermante.
    def handle_endtag(self, tag: str) -> None:
        if tag == "a" and self._capture_title:
            title = api.re.sub("\\s+", " ", "".join(self._title)).strip()
            if self._link and title:
                self.items.append({"url": self._link, "title": title, "snippet": ""})
            self._capture_title = False
            self._link = ""
            self._title = []
        if self._capture_snippet and tag in {"a", "div", "td", "span"}:
            snippet = api.re.sub("\\s+", " ", "".join(self._snippet)).strip()
            if snippet and self.items and (not self.items[-1]["snippet"]):
                self.items[-1]["snippet"] = snippet
            self._capture_snippet = False
            self._snippet = []


# Extrait le texte lisible d’une page HTML.
class _ReadableHTMLParser(api.HTMLParser):
    _BLOCKS = {
        "article",
        "aside",
        "blockquote",
        "br",
        "div",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "li",
        "main",
        "p",
        "pre",
        "section",
        "table",
        "td",
        "th",
        "tr",
    }
    _SKIP = {"canvas", "form", "head", "nav", "noscript", "script", "style", "svg"}

    # Initialise extrait le texte lisible d’une page HTML.
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title_parts: list[str] = []
        self.all_parts: list[str] = []
        self.primary_parts: list[str] = []
        self._skip_depth = 0
        self._primary_depth = 0
        self._in_title = False

    # Ajoute un fragment de texte lisible.
    def _append(self, value: str) -> None:
        if self._skip_depth:
            return
        self.all_parts.append(value)
        if self._primary_depth:
            self.primary_parts.append(value)

    # Traite une balise HTML ouvrante.
    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        del attrs
        tag = tag.lower()
        if tag in self._SKIP:
            self._skip_depth += 1
        if tag in {"article", "main"} and (not self._skip_depth):
            self._primary_depth += 1
        if tag == "title":
            self._in_title = True
        if tag in self._BLOCKS:
            self._append("\n")

    # Traite une balise HTML fermante.
    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag == "title":
            self._in_title = False
        if tag in self._BLOCKS:
            self._append("\n")
        if tag in {"article", "main"} and self._primary_depth:
            self._primary_depth -= 1
        if tag in self._SKIP and self._skip_depth:
            self._skip_depth -= 1

    # Collecte le texte d’une page HTML.
    def handle_data(self, data: str) -> None:
        if self._in_title:
            self.title_parts.append(data)
        if not self._skip_depth:
            self._append(data)

    # Retourne le texte extrait.
    def result(self) -> tuple[str, str]:
        title = api.re.sub("\\s+", " ", "".join(self.title_parts)).strip()
        preferred = (
            self.primary_parts if len("".join(self.primary_parts)) >= 120 else self.all_parts
        )
        raw = "".join(preferred).replace("\r", "")
        lines = [api.re.sub("[ \\t\\f\\v]+", " ", line).strip() for line in raw.split("\n")]
        output: list[str] = []
        for line in lines:
            if not line:
                if output and output[-1]:
                    output.append("")
                continue
            if output and output[-1] == line:
                continue
            output.append(line)
        return (title, "\n".join(output).strip())


# Construit un identifiant de résultat Web.
def _web_token(url: str) -> str:
    return api.hashlib.sha256(url.encode("utf-8")).hexdigest()[:12].upper()


# Identifie la source du résultat Web.
def _web_source(url: str) -> str:
    hostname = api.urllib.parse.urlparse(url).hostname or "web"
    return hostname[4:] if hostname.lower().startswith("www.") else hostname


# Extrait l’URL réelle de DuckDuckGo.
def _unwrap_duckduckgo_url(value: str) -> str:
    url = api.html.unescape(value.strip())
    if url.startswith("//"):
        url = "https:" + url
    elif url.startswith("/"):
        url = api.urllib.parse.urljoin("https://duckduckgo.com", url)
    parsed = api.urllib.parse.urlparse(url)
    if parsed.hostname and parsed.hostname.lower().endswith("duckduckgo.com"):
        target = api.urllib.parse.parse_qs(parsed.query).get("uddg", [""])[0]
        if target:
            url = api.urllib.parse.unquote(target)
    parsed = api.urllib.parse.urlparse(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        return ""
    return api.urllib.parse.urlunparse(parsed._replace(fragment=""))


# Normalise un résultat Web.
def _web_entry(url: str, title: str, snippet: str = "") -> api.WebEntry | None:
    cleaned_url = _unwrap_duckduckgo_url(url)
    if not cleaned_url:
        return None
    cleaned_title = api.re.sub("[\\r\\n|]+", " ", api.html.unescape(title)).strip()
    if not cleaned_title:
        return None
    cleaned_snippet = api.re.sub("\\s+", " ", api.html.unescape(snippet)).strip()
    return api.WebEntry(
        token=_web_token(cleaned_url),
        title=cleaned_title,
        source=_web_source(cleaned_url),
        snippet=cleaned_snippet,
        url=cleaned_url,
    )


# Analyse un document de résultats DuckDuckGo.
def _entries_from_duckduckgo_document(document: bytes) -> list[api.WebEntry]:
    parser = _DuckDuckGoParser()
    parser.feed(document.decode("utf-8", errors="replace"))
    entries: list[api.WebEntry] = []
    seen: set[str] = set()
    for item in parser.items:
        entry = _web_entry(item["url"], item["title"], item["snippet"])
        if entry is None or entry.url in seen:
            continue
        entries.append(entry)
        seen.add(entry.url)
        if len(entries) >= 8:
            break
    return entries


# Construit les en-têtes de recherche Web.
def _web_search_headers(config: api.BridgeConfig) -> dict[str, str]:
    language = config.language.lower()
    locale = "fr-FR,fr;q=0.9,en;q=0.7" if language.startswith("fr") else "en-US,en;q=0.9"
    return {
        "User-Agent": api.WEB_BROWSER_USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": locale,
        "Cache-Control": "no-cache",
    }


# Recherche avec le flux RSS Bing.
def _bing_rss_search(query: str, config: api.BridgeConfig) -> list[api.WebEntry]:
    language = "fr-FR" if config.language.lower().startswith("fr") else "en-US"
    parameters = api.urllib.parse.urlencode({"q": query, "format": "rss", "setlang": language})
    raw = api._request_bytes(
        f"https://www.bing.com/search?{parameters}",
        config.request_timeout_seconds,
        headers={
            "User-Agent": api.WEB_BROWSER_USER_AGENT,
            "Accept": "application/rss+xml,application/xml,text/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": language,
        },
    )
    try:
        root = api.ET.fromstring(raw)
    except api.ET.ParseError as exc:
        raise api.ServiceError("Reponse de recherche invalide") from exc
    entries: list[api.WebEntry] = []
    seen: set[str] = set()
    for item in root.iter():
        if item.tag.rsplit("}", 1)[-1].lower() != "item":
            continue
        values: dict[str, str] = {}
        for child in item:
            values[child.tag.rsplit("}", 1)[-1].lower()] = "".join(child.itertext()).strip()
        entry = _web_entry(
            values.get("link", ""),
            values.get("title", ""),
            api._clean_html(values.get("description", "")),
        )
        if entry is None or entry.url in seen:
            continue
        entries.append(entry)
        seen.add(entry.url)
        if len(entries) >= 8:
            break
    return entries


# Recherche avec DuckDuckGo.
def _duckduckgo_search(query: str, config: api.BridgeConfig) -> list[api.WebEntry]:
    market = "fr-fr" if config.language.lower().startswith("fr") else "us-en"
    parameters = api.urllib.parse.urlencode({"q": query, "kl": market})
    headers = _web_search_headers(config)
    errors: list[str] = []
    for endpoint in (
        f"https://html.duckduckgo.com/html/?{parameters}",
        f"https://lite.duckduckgo.com/lite/?{parameters}",
    ):
        try:
            entries = _entries_from_duckduckgo_document(
                api._request_bytes(endpoint, config.request_timeout_seconds, headers=headers)
            )
            if entries:
                return entries
        except api.ServiceError as exc:
            errors.append(str(exc))
    try:
        entries = _bing_rss_search(query, config)
        if entries:
            return entries
    except api.ServiceError as exc:
        errors.append(str(exc))
    detail = errors[-1] if errors else "aucune reponse exploitable"
    raise api.ServiceError(f"Recherche Web indisponible: {detail}")


# Recherche avec SearXNG.
def _searxng_search(query: str, config: api.BridgeConfig) -> list[api.WebEntry]:
    if not config.searxng_url:
        raise api.ServiceError("Configure searxng_url dans config.json")
    parameters = api.urllib.parse.urlencode(
        {"q": query, "format": "json", "language": config.language, "safesearch": 1}
    )
    payload = api._request_json(
        f"{config.searxng_url.rstrip('/')}/search?{parameters}", config.request_timeout_seconds
    )
    entries: list[api.WebEntry] = []
    seen: set[str] = set()
    for item in payload.get("results", []):
        if not isinstance(item, dict):
            continue
        entry = _web_entry(
            str(item.get("url", "")), str(item.get("title", "")), str(item.get("content", ""))
        )
        if entry is None or entry.url in seen:
            continue
        entries.append(entry)
        seen.add(entry.url)
        if len(entries) >= 8:
            break
    return entries


# Essaie les fournisseurs de recherche Web.
def _web_search_entries(query: str, config: api.BridgeConfig) -> list[api.WebEntry]:
    if config.web_search_provider == "searxng":
        return _searxng_search(query, config)
    return _duckduckgo_search(query, config)


# Construit le contenu d’un résultat Web.
def _web_result_body(entries: list[dict[str, api.Any]]) -> str:
    lines = ["FNWEB1"]
    for item in entries[:8]:
        token = str(item.get("token", "")).upper()
        title = str(item.get("title", "Web result"))
        source = str(item.get("source", "web"))
        label = api._short(f"{title} - {source}", 76).replace("|", "/")
        if api.re.fullmatch("[A-F0-9]{8,16}", token):
            lines.append(f"{token}|{label}")
    if len(lines) == 1:
        raise api.ServiceError("Recherche Web sans resultat exploitable")
    return "\n".join(lines)


# Recherche des pages Web.
def web_search(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    query = " ".join(argument.split())
    if len(query) < 2:
        raise api.ServiceError(
            api.tr(
                config.language[:2], "Enter at least 2 characters", "Saisis au moins 2 caracteres"
            )
        )
    store = api._need_state(state)
    try:
        entries = _web_search_entries(query, config)
        if not entries:
            raise api.ServiceError("Recherche Web sans resultat exploitable")
        cached = store.cache_web_search(
            query,
            [
                {
                    "token": item.token,
                    "title": item.title,
                    "source": item.source,
                    "snippet": item.snippet,
                    "url": item.url,
                }
                for item in entries
            ],
        )
        return api.ServiceResult("Web results", _web_result_body(cached))
    except api.ServiceError:
        cached = store.cached_web_search(query)
        if cached:
            return api.ServiceResult("Cached Web results", _web_result_body(cached))
        raise


# Valide une URL Web publique.
def _validate_public_web_url(value: str) -> str:
    raw = value.strip()
    if not raw:
        raise api.ServiceError("Indique une URL")
    if "://" not in raw:
        raw = "https://" + raw
    parsed = api.urllib.parse.urlparse(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise api.ServiceError("URL HTTP ou HTTPS invalide")
    if parsed.username or parsed.password:
        raise api.ServiceError("Identifiants interdits dans l URL")
    hostname = parsed.hostname.rstrip(".").lower()
    if hostname == "localhost" or hostname.endswith((".local", ".localhost")):
        raise api.ServiceError("Adresse locale interdite")
    try:
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        addresses = {item[4][0] for item in api.socket.getaddrinfo(hostname, port)}
    except OSError as exc:
        raise api.ServiceError(f"Domaine introuvable: {exc}") from exc
    if not addresses:
        raise api.ServiceError("Domaine introuvable")
    for address in addresses:
        try:
            ip = api.ipaddress.ip_address(address.split("%", 1)[0])
        except ValueError as exc:
            raise api.ServiceError("Adresse IP invalide") from exc
        if not ip.is_global:
            raise api.ServiceError("Adresse privee ou locale interdite")
    return api.urllib.parse.urlunparse(parsed._replace(fragment=""))


# Bloque les redirections vers les réseaux privés.
class _PublicRedirectHandler(api.urllib.request.HTTPRedirectHandler):
    # Valide chaque redirection HTTP.
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        safe_url = _validate_public_web_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, safe_url)


# Décode le contenu téléchargé.
def _decode_web_payload(payload: bytes, content_type: str) -> str:
    match = api.re.search("charset=([A-Za-z0-9._-]+)", content_type, api.re.IGNORECASE)
    encoding = match.group(1) if match else "utf-8"
    try:
        return payload.decode(encoding, errors="replace")
    except LookupError:
        return payload.decode("utf-8", errors="replace")


# Télécharge une URL Web publique.
def _read_web_url(url: str, config: api.BridgeConfig) -> api.ServiceResult:
    safe_url = _validate_public_web_url(url)
    request = api.urllib.request.Request(
        safe_url,
        headers={
            "User-Agent": api.USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,text/plain;q=0.9",
        },
        method="GET",
    )
    opener = api.urllib.request.build_opener(_PublicRedirectHandler())
    try:
        with opener.open(request, timeout=config.request_timeout_seconds) as response:
            content_type = response.headers.get("Content-Type", "text/html").lower()
            readable_types = ("text/html", "application/xhtml+xml", "text/plain")
            if not any(item in content_type for item in readable_types):
                raise api.ServiceError("Type de page non lisible")
            payload = response.read(1000001)
            if len(payload) > 1000000:
                raise api.ServiceError("Page trop volumineuse")
            final_url = _validate_public_web_url(response.geturl())
    except api.ServiceError:
        raise
    except Exception as exc:
        raise api.ServiceError(f"Lecture Web impossible: {exc}") from exc
    decoded = _decode_web_payload(payload, content_type)
    if "text/plain" in content_type:
        title = _web_source(final_url)
        body = decoded
    else:
        parser = _ReadableHTMLParser()
        parser.feed(decoded)
        title, body = parser.result()
        title = title or _web_source(final_url)
    body = api.re.sub("\\n{3,}", "\n\n", body).strip()
    if len(body) < 40:
        raise api.ServiceError("Page sans texte lisible")
    return api.ServiceResult(api._short(title, 64), body[:6000])


# Charge une page depuis les résultats.
def web_page(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    token = argument.strip().upper()
    if not api.re.fullmatch("[A-F0-9]{8,16}", token):
        raise api.ServiceError("Resultat Web invalide")
    store = api._need_state(state)
    try:
        entry = store.web_result(token)
    except KeyError as exc:
        raise api.ServiceError("Resultat Web expire") from exc
    try:
        result = _read_web_url(str(entry.get("url", "")), config)
        store.cache_web_page(token, result.title, result.body)
        source = str(entry.get("source", "web"))
        return api.ServiceResult(result.title, f"Source: {source}\n\n{result.body}")
    except api.ServiceError:
        cached_body = str(entry.get("page_body", "")).strip()
        if cached_body:
            cached_title = str(entry.get("page_title", "")) or str(entry.get("title", "Web page"))
            return api.ServiceResult(cached_title, f"Cached page\n\n{cached_body}")
        raise


# Charge directement une URL publique.
def web_url(argument: str, config: api.BridgeConfig, state: StateStore | None) -> api.ServiceResult:
    safe_url = _validate_public_web_url(argument)
    entry = _web_entry(safe_url, _web_source(safe_url))
    if entry is None:
        raise api.ServiceError("URL invalide")
    store = api._need_state(state)
    store.cache_web_search(
        safe_url,
        [
            {
                "token": entry.token,
                "title": entry.title,
                "source": entry.source,
                "snippet": "",
                "url": entry.url,
            }
        ],
    )
    return web_page(entry.token, config, store)


# Construit le catalogue des applications.
