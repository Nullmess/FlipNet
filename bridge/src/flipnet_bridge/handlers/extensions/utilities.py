"""Gère les applications en ligne."""

from __future__ import annotations

from typing import TYPE_CHECKING

from .. import shared as api

if TYPE_CHECKING:
    pass



# Recherche les choix d’une application.
def app_search(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    try:
        body = api.utility_runtime.search_payload(argument, config)
    except api.utility_runtime.OnlineAppError as exc:
        raise api.ServiceError(str(exc)) from exc
    return api.ServiceResult("Choose", body)


# Exécute une application dynamique.
def app_run(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    try:
        title, body = api.utility_runtime.run(argument, config)
    except api.utility_runtime.OnlineAppError as exc:
        raise api.ServiceError(str(exc)) from exc
    return api.ServiceResult(title, body)
