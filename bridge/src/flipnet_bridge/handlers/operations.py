"""Gère les opérations."""

from __future__ import annotations

from typing import TYPE_CHECKING

from . import shared as api
from .systems import _powershell, _run_text

if TYPE_CHECKING:
    from ..core.state import StateStore

_ACTION_NAME = api.re.compile("^[A-Za-z0-9_.@:-]{1,96}$")


# Distribue une requête sans dépendance circulaire.
def _dispatch(service_id: str, argument: str, config: api.BridgeConfig, state):
    from ..registry.runtime import dispatch

    return dispatch(service_id, argument, config, state)


# Construit le tableau de bord.
def dashboard(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    store = api._need_state(state)
    counts = store.counts()
    try:
        inventory = store.inventory()
        devices = len(inventory.get("assets", []))
        last_sync = str(inventory.get("scanned_at", ""))
    except KeyError:
        devices = 0
        last_sync = "never"
    changes = store.inventory_changes()
    latest = changes[0] if changes else {}
    body = f"Gateway=OK\nInternet=OK\nDevices={devices}\nNew={len(latest.get('added', []))}\nMissing={len(latest.get('removed', []))}\nChanged={len(latest.get('changed', []))}\nMonitors={counts['monitors']}\nOK={counts['monitor_ok']}\nError={counts['monitor_error']}\nAlerts={counts['alerts']}\nTargets={counts['targets']}\nFavorites={counts['favorites']}\nHistory={counts['history']}\nLastSync={last_sync}"
    return api.ServiceResult("Smart Dashboard", body)


# Liste les événements récents.
def timeline_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    items = api._need_state(state).timeline()
    if not items:
        return api.ServiceResult("Timeline", "No recent events")
    lines = []
    for item in items[:12]:
        stamp = str(item.get("timestamp", ""))[11:16] or "--:--"
        level = str(item.get("severity", "INFO"))[:4]
        lines.append(f"{stamp} [{level}] {api._short(item.get('title', '?'), 48)}")
    return api.ServiceResult("Timeline", "\n".join(lines))


# Liste les surveillances.
def monitors_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    items = api._need_state(state).monitors()
    if not items:
        return api.ServiceResult(
            "Monitors", "Aucun monitor.\nAjoute une cible depuis le menu Monitors."
        )
    lines = []
    for item in items[:12]:
        status = str(item.get("last_status", "NEW"))
        name = api._short(item.get("name", "Monitor"), 36)
        lines.append(f"#{item.get('id')} [{status}] {name}")
    return api.ServiceResult("Monitors", "\n".join(lines))


# Analyse les paramètres d’une surveillance.
def _parse_monitor_argument(argument: str) -> tuple[str, str, str, int]:
    parts = [part.strip() for part in argument.split("|")]
    if len(parts) == 1:
        target = parts[0]
        return (target, "endpoint_check", target, 60)
    if len(parts) == 2:
        return (parts[0] or parts[1], "endpoint_check", parts[1], 60)
    if len(parts) in {3, 4}:
        name, service, target = parts[:3]
        interval = int(parts[3]) if len(parts) == 4 and parts[3].isdigit() else 60
        return (name or target, service or "endpoint_check", target, interval)
    raise api.ServiceError("Format: Nom|cible ou Nom|service|argument|secondes")


# Ajoute une surveillance.
def monitor_add(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del config
    if not argument.strip():
        raise api.ServiceError("Indique Nom|cible")
    name, service, target, interval = _parse_monitor_argument(argument)
    allowed = {"endpoint_check", "domain_audit", "tls", "dns", "headers"}
    service = service.strip().lower().replace("-", "_")
    if service not in allowed:
        raise api.ServiceError("Monitor supporte: endpoint_check, domain_audit, tls, dns, headers")
    item = api._need_state(state).add_monitor(name, service, target, interval)
    return api.ServiceResult(
        "Monitor added", f"#{item['id']} {item['name']}\nEvery {item['interval_seconds']} s"
    )


# Retire une surveillance.
def monitor_remove(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del config
    try:
        monitor_id = int(argument.strip().lstrip("#"))
    except ValueError as exc:
        raise api.ServiceError("Indique l'identifiant du monitor") from exc
    if not api._need_state(state).remove_monitor(monitor_id):
        raise api.ServiceError("Monitor introuvable")
    return api.ServiceResult("Monitor removed", f"#{monitor_id}")


# Exécute une surveillance.
def monitor_run(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument
    store = api._need_state(state)
    items = store.monitors()
    if not items:
        return api.ServiceResult("Monitor run", "Aucun monitor configure")
    now = api.time.time()
    ok = 0
    failed = 0
    for item in items:
        try:
            service_name = str(item.get("service"))
            result = _dispatch(service_name, str(item.get("argument")), config, None)
            title, body = (result.title, result.body)
            normalized = body.upper()
            healthy = not (
                service_name == "endpoint_check"
                and ("FERME/TIMEOUT" in normalized or "ECHEC" in normalized)
                or (service_name == "domain_audit" and "ECHEC" in normalized)
            )
            status = "OK" if healthy else "ERR"
            if healthy:
                ok += 1
            else:
                failed += 1
        except Exception as exc:
            status = "ERR"
            failed += 1
            title = str(item.get("name") or "Monitor")
            body = str(exc)
        previous, updated = store.update_monitor(int(item["id"]), status, title, body, now)
        if updated is not None and previous not in {"NEW", status}:
            prefix = "ALERTE" if status == "ERR" else "RETABLI"
            store.add_alert(f"{prefix}: {updated.get('name')}", body)
    return api.ServiceResult("Monitor run", f"{ok} OK\n{failed} ERR\n{len(items)} checked")


# Liste les alertes.
def alerts_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    items = [item for item in api._need_state(state).alerts() if not item.get("acknowledged")]
    if not items:
        return api.ServiceResult("Alerts", "Aucune alerte active")
    lines = [
        f"{index + 1}. {api._short(item.get('title'), 52)}" for index, item in enumerate(items[:10])
    ]
    return api.ServiceResult("Alerts", "\n".join(lines))


# Efface les alertes.
def alerts_clear(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    api._need_state(state).clear_alerts()
    return api.ServiceResult("Alerts", "Alertes acquittees")


# Vérifie si les actions sont autorisées.
def _actions_enabled(config: api.BridgeConfig) -> None:
    if not config.secure_actions_enabled:
        raise api.ServiceError(
            "Actions desactivees. Active secure_actions_enabled dans la configuration."
        )


# Sépare les champs d’une action.
def _parse_action_argument(argument: str) -> tuple[str, str, str]:
    parts = [part.strip() for part in argument.split("|", 2)]
    if len(parts) != 3 or not all(parts):
        raise api.ServiceError("Format: type|action|cible")
    kind, operation, target = parts
    kind = kind.lower()
    operation = operation.lower()
    if kind not in {"service", "workload"} or operation != "restart":
        raise api.ServiceError("Action supportee: restart service ou workload")
    if not _ACTION_NAME.fullmatch(target):
        raise api.ServiceError("Cible invalide")
    return (kind, operation, target)


# Valide la cible d’une action.
def _allowed_action_target(kind: str, target: str, config: api.BridgeConfig) -> bool:
    allowed = (
        config.allowed_service_actions if kind == "service" else config.allowed_workload_actions
    )
    return target in allowed


# Redémarre un service autorisé.
def _restart_service(target: str) -> str:
    prefix, separator, name = target.partition(":")
    if not separator or not name:
        raise api.ServiceError("Cible attendue: user:, system:, openrc: ou windows:")
    system = api.platform.system().lower()
    if system == "linux":
        if prefix == "user":
            command = ["systemctl", "--user", "restart", name]
        elif prefix == "system":
            command = ["systemctl", "restart", name]
        elif prefix == "openrc":
            command = ["rc-service", name, "restart"]
        else:
            raise api.ServiceError("Prefixe Linux invalide")
    elif system == "windows":
        if prefix != "windows":
            raise api.ServiceError("Prefixe Windows attendu")
        code, output, error = _powershell(
            f"Restart-Service -Name '{name}' -ErrorAction Stop; (Get-Service -Name '{name}').Status",
            15,
        )
        if code != 0:
            raise api.ServiceError(api._short(error or output or "Echec du service", 160))
        return output or "Restarted"
    elif system == "darwin":
        if prefix == "user":
            domain = f"gui/{api.os.getuid()}"
        elif prefix == "system":
            domain = "system"
        else:
            raise api.ServiceError("Prefixe macOS invalide")
        command = ["launchctl", "kickstart", "-k", f"{domain}/{name}"]
    else:
        raise api.ServiceError("Action service non disponible sur cette plateforme")
    code, output, error = _run_text(command, 15)
    if code != 0:
        raise api.ServiceError(api._short(error or output or "Echec du service", 160))
    return output or "Restarted"


# Redémarre une charge de travail autorisée.
def _restart_workload(target: str) -> str:
    runtime, separator, name = target.partition(":")
    if not separator or runtime not in {"podman", "docker"} or (not name):
        raise api.ServiceError("Cible attendue: podman:nom ou docker:nom")
    code, output, error = _run_text([runtime, "restart", name], 30)
    if code != 0:
        raise api.ServiceError(api._short(error or output or "Echec du workload", 160))
    return output or name


# Charge les procédures configurées.
def _playbook_definitions(config: api.BridgeConfig) -> list[dict[str, api.Any]]:
    playbooks: list[dict[str, api.Any]] = []
    seen: set[str] = set()
    for raw in config.playbooks[:24]:
        if not isinstance(raw, dict):
            continue
        playbook_id = str(raw.get("id", "")).strip().lower().replace("_", "-")[:32]
        if (
            not playbook_id
            or playbook_id in seen
            or (not api.re.fullmatch("[a-z0-9][a-z0-9-]*", playbook_id))
        ):
            continue
        title = str(raw.get("title", playbook_id)).strip()[:48] or playbook_id
        title_fr = str(raw.get("title_fr", title)).strip()[:48] or title
        rollback = str(raw.get("rollback", "")).strip()[:180]
        rollback_fr = str(raw.get("rollback_fr", rollback)).strip()[:180]
        steps: list[dict[str, str]] = []
        for raw_step in raw.get("steps", [])[:8] if isinstance(raw.get("steps"), list) else []:
            if not isinstance(raw_step, dict):
                continue
            kind = str(raw_step.get("kind", "")).strip().lower()
            operation = str(raw_step.get("operation", "restart")).strip().lower()
            target = str(raw_step.get("target", "")).strip()
            if kind not in {"service", "workload"} or operation != "restart":
                continue
            if not _ACTION_NAME.fullmatch(target) or not _allowed_action_target(
                kind, target, config
            ):
                continue
            steps.append({"kind": kind, "operation": operation, "target": target})
        if not steps:
            continue
        seen.add(playbook_id)
        playbooks.append(
            {
                "id": playbook_id,
                "title": title,
                "title_fr": title_fr,
                "rollback": rollback,
                "rollback_fr": rollback_fr,
                "steps": steps,
            }
        )
    return playbooks


# Trouve une procédure par identifiant.
def _playbook_by_id(argument: str, config: api.BridgeConfig) -> dict[str, api.Any]:
    wanted = argument.strip().lower().replace("_", "-")
    for playbook in _playbook_definitions(config):
        if playbook["id"] == wanted:
            return playbook
    raise api.ServiceError("Playbook introuvable ou non autorise")


# Formate le contenu d’une procédure.
def _playbook_text(playbook: dict[str, api.Any], config: api.BridgeConfig) -> tuple[str, str]:
    french = str(config.language).lower().startswith("fr")
    title = str(playbook["title_fr"] if french else playbook["title"])
    body_lines: list[str] = []
    for index, step in enumerate(playbook["steps"], start=1):
        body_lines.extend([f"{index}. {step['operation']} {step['kind']}", f"   {step['target']}"])
    rollback = str(playbook["rollback_fr"] if french else playbook["rollback"])
    if rollback:
        body_lines.extend(["", "Recovery:" if not french else "Recuperation:", rollback])
    return (title, "\n".join(body_lines))


# Liste les procédures.
def playbook_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, state
    _actions_enabled(config)
    items = _playbook_definitions(config)
    if not items:
        return api.ServiceResult("Playbooks", "Aucun playbook configure")
    french = str(config.language).lower().startswith("fr")
    lines = [
        f"{item['id']} | {(item['title_fr'] if french else item['title'])}" for item in items[:12]
    ]
    return api.ServiceResult("Playbooks", "\n".join(lines))


# Affiche les étapes d’une procédure.
def playbook_preview(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del state
    _actions_enabled(config)
    playbook = _playbook_by_id(argument, config)
    title, body = _playbook_text(playbook, config)
    return api.ServiceResult(title, body)


# Prépare une procédure protégée.
def playbook_prepare(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    _actions_enabled(config)
    playbook = _playbook_by_id(argument, config)
    title, body = _playbook_text(playbook, config)
    action = api._need_state(state).prepare_action(
        "playbook",
        "run",
        str(playbook["id"]),
        config.secure_action_ttl_seconds,
        {"steps": playbook["steps"], "rollback": playbook["rollback"], "playbook_title": title},
    )
    seconds = max(0, int(float(action["expires_epoch"]) - api.time.time()))
    return api.ServiceResult(
        "Playbook ready",
        f"{title}\nSteps: {len(playbook['steps'])}\nExpires: {seconds} s\n\n{body}\n\nReturn and choose CONFIRM.",
    )


# Prépare une action protégée.
def action_prepare(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    _actions_enabled(config)
    kind, operation, target = _parse_action_argument(argument)
    if not _allowed_action_target(kind, target, config):
        raise api.ServiceError("Cible absente de la liste blanche")
    action = api._need_state(state).prepare_action(
        kind, operation, target, config.secure_action_ttl_seconds
    )
    seconds = max(0, int(float(action["expires_epoch"]) - api.time.time()))
    body = f"Type: {kind}\nAction: {operation}\nTarget: {target}\nExpires: {seconds} s\nReturn to Secure Actions and choose CONFIRM."
    return api.ServiceResult("Action ready", body)


# Affiche l’action en attente.
def action_pending(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument
    _actions_enabled(config)
    try:
        action = api._need_state(state).pending_action()
    except IndexError as exc:
        raise api.ServiceError(str(exc)) from exc
    seconds = max(0, int(float(action["expires_epoch"]) - api.time.time()))
    return api.ServiceResult(
        "Pending action",
        f"{action['operation']} {action['kind']}\n{action['target']}\nExpires: {seconds} s",
    )


# Confirme puis exécute l’action.
def action_commit(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    _actions_enabled(config)
    store = api._need_state(state)
    try:
        action = store.consume_action(argument.strip() or "last")
    except IndexError as exc:
        raise api.ServiceError(str(exc)) from exc
    try:
        if action["kind"] == "service":
            detail = _restart_service(str(action["target"]))
        elif action["kind"] == "workload":
            detail = _restart_workload(str(action["target"]))
        elif action["kind"] == "playbook":
            completed: list[str] = []
            for index, step in enumerate(action.get("steps", []), start=1):
                kind = str(step.get("kind", ""))
                target = str(step.get("target", ""))
                if not _allowed_action_target(kind, target, config):
                    raise api.ServiceError(f"Etape {index}: cible retiree de la liste blanche")
                if kind == "service":
                    _restart_service(target)
                elif kind == "workload":
                    _restart_workload(target)
                else:
                    raise api.ServiceError(f"Etape {index}: type non supporte")
                completed.append(target)
            detail = f"{len(completed)} step(s): " + ", ".join(completed)
        else:
            raise api.ServiceError("Type d'action non supporte")
    except Exception as exc:
        store.record_action(action, "FAILED", str(exc))
        if isinstance(exc, api.ServiceError):
            raise
        raise api.ServiceError(str(exc)) from exc
    store.record_action(action, "OK", detail)
    return api.ServiceResult(
        "Action complete", f"{action['operation']} {action['kind']}\n{action['target']}\nStatus: OK"
    )


# Annule l’action en attente.
def action_cancel(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    _actions_enabled(config)
    try:
        action = api._need_state(state).cancel_action(argument.strip() or "last")
    except IndexError as exc:
        raise api.ServiceError(str(exc)) from exc
    return api.ServiceResult("Action cancelled", str(action.get("target", "")))


# Liste le journal des actions.
def action_log(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument
    _actions_enabled(config)
    items = api._need_state(state).action_log()
    if not items:
        return api.ServiceResult("Action log", "No action recorded")
    lines = []
    for index, item in enumerate(items[:10], start=1):
        lines.append(
            f"{index}. [{item.get('status', '?')}] {item.get('operation', '?')} {item.get('target', '?')}"
        )
    return api.ServiceResult("Action log", "\n".join(lines))
