"""Gère les services réseau."""

from __future__ import annotations

from typing import TYPE_CHECKING

from . import shared as api

if TYPE_CHECKING:
    from ..core.state import StateStore


# Normalise la cible HTTP.
def _http_target(argument: str) -> str:
    raw = argument.strip()
    if not raw:
        raise api.ServiceError("Indique un domaine ou une URL")
    if "://" not in raw:
        raw = "https://" + raw
    parsed = api.urllib.parse.urlparse(raw)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise api.ServiceError("URL HTTP ou HTTPS invalide")
    return raw


# Envoie une requête HTTP bornée.
def _http_probe(url: str, timeout: float) -> dict[str, api.Any]:
    request = api.urllib.request.Request(
        url,
        headers={
            "User-Agent": api.USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,*/*",
            "Range": "bytes=0-0",
        },
        method="GET",
    )
    started = api.time.perf_counter()
    try:
        response = api.urllib.request.urlopen(request, timeout=timeout)
    except api.urllib.error.HTTPError as exc:
        response = exc
    elapsed_ms = int((api.time.perf_counter() - started) * 1000)
    try:
        status = int(getattr(response, "status", getattr(response, "code", 0)) or 0)
        final_url = response.geturl()
        headers = {key.lower(): value for key, value in response.headers.items()}
    finally:
        response.close()
    final = api.urllib.parse.urlparse(final_url)
    https = final.scheme.lower() == "https"
    checks = {
        "HSTS": bool(headers.get("strict-transport-security")) if https else False,
        "CSP": bool(headers.get("content-security-policy")),
        "XFO": bool(headers.get("x-frame-options"))
        or "frame-ancestors" in headers.get("content-security-policy", "").lower(),
        "XCTO": headers.get("x-content-type-options", "").lower() == "nosniff",
        "Referrer": bool(headers.get("referrer-policy")),
    }
    return {
        "status": status,
        "final_url": final_url,
        "host": final.hostname or api.urllib.parse.urlparse(url).hostname or "HTTP",
        "https": https,
        "redirected": final_url.rstrip("/") != url.rstrip("/"),
        "checks": checks,
        "score": sum(checks.values()),
        "server": headers.get("server", ""),
        "elapsed_ms": elapsed_ms,
    }


# Analyse les en-têtes de sécurité HTTP.
def http_security(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    url = _http_target(argument)
    try:
        probe = _http_probe(url, config.request_timeout_seconds)
    except Exception as exc:
        raise api.ServiceError(f"Connexion HTTP impossible: {exc}") from exc
    lines = [
        f"HTTP: {probe['status'] or '?'} | HTTPS: {('oui' if probe['https'] else 'non')}",
        f"Redirection: {('oui' if probe['redirected'] else 'non')}",
        f"Score headers: {probe['score']}/5",
    ]
    lines.extend(
        (f"{name}: {('OK' if enabled else 'absent')}" for name, enabled in probe["checks"].items())
    )
    if probe["server"]:
        lines.append(f"Serveur: {api._short(probe['server'], 70)}")
    return api.ServiceResult(str(probe["host"]), "\n".join(lines))


# Sépare un hôte et un port.
def _host_port(argument: str, default_port: int = 443) -> tuple[str, int]:
    raw = argument.strip()
    if not raw:
        raise api.ServiceError("Indique un domaine")
    if "://" not in raw:
        raw = "//" + raw
    parsed = api.urllib.parse.urlparse(raw)
    host = parsed.hostname or ""
    try:
        port = parsed.port or default_port
    except ValueError as exc:
        raise api.ServiceError("Port invalide") from exc
    if not host or not 1 <= port <= 65535:
        raise api.ServiceError("Domaine ou port invalide")
    return (host, port)


# Ouvre une connexion TLS bornée.
def _tls_probe(host: str, port: int, timeout: float) -> dict[str, api.Any]:
    context = api.ssl.create_default_context()
    started = api.time.perf_counter()
    with api.socket.create_connection((host, port), timeout=timeout) as raw:
        with context.wrap_socket(raw, server_hostname=host) as secure:
            certificate = secure.getpeercert()
            protocol = secure.version() or "?"
            cipher = secure.cipher()[0] if secure.cipher() else "?"
    elapsed_ms = int((api.time.perf_counter() - started) * 1000)

    # Lit un attribut du certificat.
    def name_value(entries: api.Any, key: str) -> str:
        for group in entries or []:
            for item_key, item_value in group:
                if item_key == key:
                    return str(item_value)
        return "?"

    subject = name_value(certificate.get("subject"), "commonName")
    issuer = name_value(certificate.get("issuer"), "organizationName")
    if issuer == "?":
        issuer = name_value(certificate.get("issuer"), "commonName")
    not_after = str(certificate.get("notAfter") or "")
    days: int | None = None
    expiry = not_after
    if not_after:
        try:
            timestamp = api.ssl.cert_time_to_seconds(not_after)
            expires = api.datetime.fromtimestamp(timestamp, api.UTC)
            expiry = expires.strftime("%Y-%m-%d")
            days = (expires - api.datetime.now(api.UTC)).days
        except Exception:
            pass
    return {
        "subject": subject,
        "issuer": issuer,
        "expiry": expiry,
        "days": days,
        "protocol": protocol,
        "cipher": cipher,
        "sans": len(certificate.get("subjectAltName") or []),
        "elapsed_ms": elapsed_ms,
    }


# Analyse le certificat TLS.
def tls_inspect(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    host, port = _host_port(argument)
    try:
        probe = _tls_probe(host, port, config.request_timeout_seconds)
    except api.ssl.SSLCertVerificationError as exc:
        return api.ServiceResult(host, f"TLS: INVALIDE\n{api._short(exc.verify_message, 240)}")
    except Exception as exc:
        raise api.ServiceError(f"Connexion TLS impossible: {exc}") from exc
    days = "?" if probe["days"] is None else str(probe["days"])
    body = f"TLS: valide | {probe['protocol']}\nSujet: {api._short(probe['subject'], 80)}\nEmetteur: {api._short(probe['issuer'], 80)}\nExpire: {probe['expiry']} ({days} j)\nChiffrement: {api._short(probe['cipher'], 80)}\nSAN: {probe['sans']} | Port: {port}"
    return api.ServiceResult(host, body)


# Résout les adresses IP de la cible.
def _resolve_addresses(host: str) -> tuple[list[str], list[str]]:
    answers = api.socket.getaddrinfo(host, None, api.socket.AF_UNSPEC, api.socket.SOCK_STREAM)
    ipv4: list[str] = []
    ipv6: list[str] = []
    for family, _, _, _, sockaddr in answers:
        address = str(sockaddr[0])
        target = ipv6 if family == api.socket.AF_INET6 else ipv4
        if address not in target:
            target.append(address)
    return (ipv4, ipv6)


# Résout les enregistrements DNS.
def dns_lookup(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del config
    host, _ = _host_port(argument, 53)
    try:
        ipv4, ipv6 = _resolve_addresses(host)
    except api.socket.gaierror as exc:
        raise api.ServiceError(f"DNS introuvable: {exc}") from exc
    lines: list[str] = []
    if ipv4:
        lines.append("IPv4:")
        lines.extend(f"- {value}" for value in ipv4[:5])
    if ipv6:
        lines.append("IPv6:")
        lines.extend(f"- {value}" for value in ipv6[:4])
    if not lines:
        raise api.ServiceError("Aucune adresse trouvee")
    return api.ServiceResult(host, "\n".join(lines))


# Contrôle les protections du domaine.
def domain_audit(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    url = _http_target(argument)
    parsed = api.urllib.parse.urlparse(url)
    host = parsed.hostname or ""
    lines: list[str] = []
    try:
        ipv4, ipv6 = _resolve_addresses(host)
        lines.append(f"DNS: OK | A {len(ipv4)} | AAAA {len(ipv6)}")
    except Exception:
        lines.append("DNS: ECHEC")
    try:
        tls = _tls_probe(host, parsed.port or 443, config.request_timeout_seconds)
        days = "?" if tls["days"] is None else str(tls["days"])
        lines.append(f"TLS: {tls['protocol']} | {days} j")
        tls_latency = int(tls["elapsed_ms"])
    except Exception:
        lines.append("TLS: ECHEC")
        tls_latency = 0
    try:
        http = _http_probe(url, config.request_timeout_seconds)
        lines.append(f"HTTPS: {http['status'] or '?'} | Headers {http['score']}/5")
        lines.append(f"Redirect: {('oui' if http['redirected'] else 'non')}")
        latency = int(http["elapsed_ms"])
    except Exception:
        lines.append("HTTP: ECHEC")
        latency = tls_latency
    if latency:
        lines.append(f"Latence: {latency} ms")
    return api.ServiceResult(host, "\n".join(lines))


# Vérifie la disponibilité d’un point de terminaison.
def endpoint_check(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    raw = argument.strip()
    if not raw:
        raise api.ServiceError("Indique host:port ou une URL")
    is_url = "://" in raw
    if is_url:
        parsed = api.urllib.parse.urlparse(raw)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise api.ServiceError("URL invalide")
        host = parsed.hostname
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        scheme = parsed.scheme
        url = raw
    else:
        host, port = _host_port(raw, 443)
        scheme = "https" if port == 443 else ""
        url = f"https://{host}:{port}" if port != 443 else f"https://{host}"
    lines: list[str] = []
    try:
        ipv4, ipv6 = _resolve_addresses(host)
        lines.append(f"DNS: OK | {len(ipv4) + len(ipv6)} adresse(s)")
    except Exception:
        lines.append("DNS: ECHEC")
    started = api.time.perf_counter()
    try:
        with api.socket.create_connection((host, port), timeout=config.request_timeout_seconds):
            pass
        tcp_ms = int((api.time.perf_counter() - started) * 1000)
        lines.append(f"TCP {port}: OUVERT | {tcp_ms} ms")
    except Exception:
        lines.append(f"TCP {port}: FERME/TIMEOUT")
        return api.ServiceResult(host, "\n".join(lines))
    if scheme == "https" or port == 443:
        try:
            tls = _tls_probe(host, port, config.request_timeout_seconds)
            lines.append(f"TLS: {tls['protocol']} | valide")
        except Exception:
            lines.append("TLS: ECHEC")
    if is_url or port in {80, 443, 8080, 8443}:
        try:
            http = _http_probe(url, config.request_timeout_seconds)
            lines.append(f"HTTP: {http['status'] or '?'} | {http['elapsed_ms']} ms")
        except Exception:
            lines.append("HTTP: ECHEC")
    return api.ServiceResult(host, "\n".join(lines))


# Teste un port unique.
def _scan_one(host: str, port: int, timeout: float) -> tuple[int, bool, int]:
    started = api.time.perf_counter()
    try:
        with api.socket.create_connection((host, port), timeout=timeout):
            elapsed = max(1, int((api.time.perf_counter() - started) * 1000))
            return (port, True, elapsed)
    except OSError:
        return (port, False, 0)


# Valide la cible de l analyse reseau.
def _scan_target(argument: str) -> str:
    raw = argument.strip()
    if not raw:
        raise api.ServiceError("Indique un hote autorise")
    host, _ = _host_port(raw, 443)
    return host


# Retourne le nom connu d un service TCP.
def _port_name(port: int) -> str:
    try:
        return api.socket.getservbyport(port, "tcp").upper()
    except OSError:
        return "TCP"


# Analyse une partie de la plage TCP.
def _scan_stride(
    address: str, timeout: float, first_port: int, step: int
) -> list[tuple[int, int]]:
    opened: list[tuple[int, int]] = []
    for port in range(first_port, 65536, step):
        _, is_open, elapsed = _scan_one(address, port, timeout)
        if is_open:
            opened.append((port, elapsed))
    return opened


# Analyse toute la plage TCP.
def _scan_all_ports(address: str, timeout: float) -> list[tuple[int, int]]:
    workers = 256
    opened: list[tuple[int, int]] = []
    with api.concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [
            pool.submit(_scan_stride, address, timeout, first, workers)
            for first in range(1, workers + 1)
        ]
        for future in api.concurrent.futures.as_completed(futures):
            opened.extend(future.result())
    opened.sort()
    return opened


# Analyse tous les ports TCP de la cible.
def port_scan(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    host = _scan_target(argument)
    ipv4, ipv6 = _resolve_addresses(host)
    addresses = ipv4 or ipv6
    if not addresses:
        raise api.ServiceError("Aucune adresse trouvee")
    timeout = min(max(config.port_scan_timeout_seconds, 0.05), 0.2)
    opened = _scan_all_ports(addresses[0], timeout)
    lines = ["Ports: 1-65535", f"Open: {len(opened)}"]
    if opened:
        for port, elapsed in opened:
            lines.append(f"{port}: OPEN | {_port_name(port)} | {elapsed} ms")
    else:
        lines.append("Aucun port TCP ouvert")
    return api.ServiceResult(host, "\n".join(lines))


# Trouve l’adresse IPv4 locale.
def _local_ipv4() -> str:
    sock = api.socket.socket(api.socket.AF_INET, api.socket.SOCK_DGRAM)
    try:
        sock.connect(("1.1.1.1", 80))
        address = str(sock.getsockname()[0])
    except OSError as exc:
        raise api.ServiceError("Impossible de trouver le reseau local") from exc
    finally:
        sock.close()
    if address.startswith("127."):
        raise api.ServiceError("Aucune interface reseau active")
    return address


# Détermine le réseau à explorer.
def _discovery_network(argument: str) -> api.ipaddress.IPv4Network:
    raw = argument.strip().lower()
    if not raw or raw in {"current", "local", "auto"}:
        return api.ipaddress.ip_network(f"{_local_ipv4()}/24", strict=False)
    try:
        network = api.ipaddress.ip_network(raw if "/" in raw else f"{raw}/24", strict=False)
    except ValueError as exc:
        raise api.ServiceError("Sous-reseau IPv4 invalide") from exc
    if not isinstance(network, api.ipaddress.IPv4Network):
        raise api.ServiceError("IPv4 uniquement")
    if network.prefixlen < 24:
        raise api.ServiceError("Limite: /24 ou plus petit")
    return network


# Teste un hôte du réseau.
def _discover_one(address: str, timeout: float) -> tuple[str, list[int]]:
    opened: list[int] = []
    for port in api.DISCOVERY_PORTS:
        _, is_open, _ = _scan_one(address, port, timeout)
        if is_open:
            opened.append(port)
            if len(opened) >= 2:
                break
    return (address, opened)


# Recherche les hôtes actifs.
def host_discovery(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    network = _discovery_network(argument)
    hosts = [str(host) for host in network.hosts()]
    if len(hosts) > 254:
        raise api.ServiceError("Sous-reseau trop grand")
    timeout = min(max(config.port_scan_timeout_seconds / 2.0, 0.08), 0.5)
    found: list[tuple[str, list[int]]] = []
    with api.concurrent.futures.ThreadPoolExecutor(max_workers=min(64, max(1, len(hosts)))) as pool:
        futures = [pool.submit(_discover_one, address, timeout) for address in hosts]
        for future in api.concurrent.futures.as_completed(futures):
            address, opened = future.result()
            if opened:
                found.append((address, opened))
    found.sort(key=lambda item: api.ipaddress.ip_address(item[0]))
    lines = [f"Reseau: {network}", f"{len(found)} hote(s) detecte(s)"]
    for address, opened in found[:18]:
        lines.append(f"{address} | {','.join(str(port) for port in opened)}")
    if len(found) > 18:
        lines.append(f"+ {len(found) - 18} autre(s)")
    if not found:
        lines.append("Aucun service TCP detecte")
    return api.ServiceResult("Host Discovery", "\n".join(lines))


# Résout le nom d’un appareil.
def _inventory_hostname(address: str) -> str:
    try:
        return api.socket.gethostbyaddr(address)[0][:120]
    except (OSError, api.socket.herror):
        return ""


# Déduit le rôle d’un appareil.
def _inventory_role(ports: list[int]) -> str:
    values = set(ports)
    if {88, 389}.intersection(values):
        return "directory"
    if 8006 in values:
        return "proxmox"
    if 8123 in values:
        return "home-assistant"
    if 53 in values:
        return "dns"
    if {80, 443}.intersection(values):
        return "web"
    if 22 in values:
        return "linux"
    if {135, 3389}.intersection(values):
        return "windows"
    return "device"


# Construit un nouvel inventaire réseau.
def inventory_scan(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    if state is None:
        raise api.ServiceError("Etat persistant indisponible")
    network = _discovery_network(argument)
    hosts = [str(host) for host in network.hosts()]
    if len(hosts) > 254:
        raise api.ServiceError("Sous-reseau trop grand")
    timeout = min(max(config.port_scan_timeout_seconds / 2.0, 0.08), 0.5)
    found: list[tuple[str, list[int]]] = []
    with api.concurrent.futures.ThreadPoolExecutor(max_workers=min(64, max(1, len(hosts)))) as pool:
        futures = [pool.submit(_discover_one, address, timeout) for address in hosts]
        for future in api.concurrent.futures.as_completed(futures):
            address, opened = future.result()
            if opened:
                found.append((address, opened))
    found.sort(key=lambda item: api.ipaddress.ip_address(item[0]))
    assets = [
        {
            "address": address,
            "hostname": _inventory_hostname(address),
            "services": opened,
            "role": _inventory_role(opened),
        }
        for address, opened in found
    ]
    change = state.save_inventory(str(network), assets)
    french = config.language == "fr"
    lines = [
        ("Reseau" if french else "Network") + f": {network}",
        ("Appareils" if french else "Devices") + f": {len(assets)}",
        ("Nouveaux" if french else "New") + f": {len(change['added'])}",
        ("Disparus" if french else "Missing") + f": {len(change['removed'])}",
        ("Modifies" if french else "Changed") + f": {len(change['changed'])}",
    ]
    for asset in assets[:14]:
        label = asset["hostname"] or asset["address"]
        ports = ",".join(str(port) for port in asset["services"])
        lines.append(f"{label} | {asset['role']} | {ports}")
    if len(assets) > 14:
        lines.append(f"+ {len(assets) - 14}")
    return api.ServiceResult(
        "Inventaire reseau" if french else "Network Inventory", "\n".join(lines)
    )


# Résume l’inventaire.
def inventory_summary(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument
    if state is None:
        raise api.ServiceError("Etat persistant indisponible")
    french = config.language == "fr"
    try:
        snapshot = state.inventory()
    except KeyError as exc:
        raise api.ServiceError("Aucun inventaire. Lance un scan.") from exc
    assets = snapshot.get("assets", [])
    roles: dict[str, int] = {}
    for asset in assets:
        role = str(asset.get("role", "device"))
        roles[role] = roles.get(role, 0) + 1
    lines = [
        ("Reseau" if french else "Network") + f": {snapshot.get('network', '?')}",
        ("Dernier scan" if french else "Last scan") + f": {snapshot.get('scanned_at', '?')}",
        ("Appareils" if french else "Devices") + f": {len(assets)}",
    ]
    lines.extend((f"{role}: {count}" for role, count in sorted(roles.items())))
    for asset in assets[:12]:
        label = asset.get("hostname") or asset.get("address")
        lines.append(f"{label} | {asset.get('role', 'device')}")
    return api.ServiceResult("Inventaire" if french else "Inventory", "\n".join(lines))


# Liste les changements réseau.
def inventory_changes(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument
    if state is None:
        raise api.ServiceError("Etat persistant indisponible")
    french = config.language == "fr"
    changes = state.inventory_changes()
    if not changes:
        return api.ServiceResult(
            "Changements" if french else "Changes", "Aucun changement" if french else "No change"
        )
    lines: list[str] = []
    for item in changes[:8]:
        lines.append(str(item.get("network", "?")))
        for key, fr, en in (
            ("added", "Nouveaux", "New"),
            ("removed", "Disparus", "Missing"),
            ("changed", "Modifies", "Changed"),
        ):
            values = item.get(key, [])
            if values:
                lines.append(f"{(fr if french else en)}: {', '.join(values[:4])}")
    return api.ServiceResult(
        "Changements reseau" if french else "Network Changes", "\n".join(lines)
    )
