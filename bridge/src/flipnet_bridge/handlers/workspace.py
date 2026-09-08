"""Gère l espace de travail persistant."""

from __future__ import annotations

from typing import TYPE_CHECKING

from . import shared as api

if TYPE_CHECKING:
    from ..core.state import StateStore

_ACTION_NAME = api.re.compile("^[A-Za-z0-9_.@:-]{1,96}$")


# Distribue une requête sans dépendance circulaire.
def _dispatch(service_id: str, argument: str, config: api.BridgeConfig, state):
    from ..registry.runtime import dispatch

    return dispatch(service_id, argument, config, state)


# Liste les cibles.
def targets_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    items = api._need_state(state).targets()
    if not items:
        return api.ServiceResult("Saved Targets", "Aucune cible enregistree")
    lines = []
    for index, item in enumerate(items[:12], start=1):
        marker = "ON" if item.get("enabled", True) else "OFF"
        lines.append(f"{index}. [{marker}] {api._short(item.get('name', '?'), 30)}")
    return api.ServiceResult("Saved Targets", "\n".join(lines))


# Affiche le détail d’une cible.
def target_detail(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del config
    key = argument.strip()
    store = api._need_state(state)
    try:
        if key.isdigit():
            items = store.targets()
            index = int(key)
            if index < 1 or index > len(items):
                raise KeyError(key)
            item = items[index - 1]
        else:
            item = store.target(key)
    except KeyError as exc:
        raise api.ServiceError("Cible introuvable") from exc
    endpoint = str(item.get("host", ""))
    if item.get("port"):
        endpoint += f":{item['port']}"
    body = f"ID={item.get('id')}\nHost={endpoint}\nProtocol={item.get('protocol', 'auto')}"
    if item.get("group_id"):
        body += f"\nGroup={item['group_id']}"
    if item.get("credential_ref"):
        body += "\nCredentials=referenced"
    return api.ServiceResult(str(item.get("name", "Target")), body)


# Liste les groupes.
def groups_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    groups = api._need_state(state).groups()
    if not groups:
        return api.ServiceResult("Target Groups", "Aucun groupe enregistre")
    return api.ServiceResult(
        "Target Groups",
        "\n".join(
            (
                f"{index}. {api._short(item.get('name', '?'), 40)}"
                for index, item in enumerate(groups[:12], start=1)
            )
        ),
    )


# Liste les favoris.
def favorites_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    items = api._need_state(state).favorites()
    if not items:
        return api.ServiceResult(
            "Favorites", "Aucun favori.\nUtilise Save last result apres une requete."
        )
    lines = [f"{index + 1}. {item.get('label', '?')}" for index, item in enumerate(items[:12])]
    return api.ServiceResult("Favorites", "\n".join(lines))


# Retourne le dernier favori.
def favorite_last(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    try:
        item = api._need_state(state).save_last_favorite()
    except ValueError as exc:
        raise api.ServiceError(str(exc)) from exc
    return api.ServiceResult("Favorite saved", str(item.get("label") or "Resultat"))


# Relance la requête d’un favori.
def favorite_run(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    try:
        index = int(argument.strip())
        item = api._need_state(state).favorite(index)
    except (ValueError, IndexError) as exc:
        raise api.ServiceError("Favori introuvable") from exc
    service = str(item.get("service", ""))
    if service in {"favorite_run", "favorite_last", "favorites"}:
        raise api.ServiceError("Favori invalide")
    target = str(item.get("argument", ""))
    result = _dispatch(service, target, config, state)
    api._need_state(state).add_history(service, target, "OK", result.title, result.body)
    return result


# Retourne la dernière requête.
def history_last(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    try:
        item = api._need_state(state).last_history()
    except IndexError as exc:
        raise api.ServiceError(str(exc)) from exc
    title = str(item.get("title") or item.get("service") or "Resultat")
    body = str(item.get("body") or "")
    return api.ServiceResult(title, body)


# Liste l’historique.
def history_list(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    items = api._need_state(state).history()
    if not items:
        return api.ServiceResult("History", "Aucun resultat recent")
    lines = []
    for index, item in enumerate(items[:10]):
        marker = "OK" if item.get("status") == "OK" else "ERR"
        label = item.get("title") or item.get("service") or "Resultat"
        lines.append(f"{index + 1}. [{marker}] {api._short(label, 46)}")
    return api.ServiceResult("History", "\n".join(lines))


# Efface l’historique.
def history_clear(
    argument: str, config: api.BridgeConfig, state: StateStore | None
) -> api.ServiceResult:
    del argument, config
    api._need_state(state).clear_history()
    return api.ServiceResult("History", "Historique efface")
