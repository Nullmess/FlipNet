"""Exécute la surveillance périodique."""

from __future__ import annotations

import asyncio
import logging
import time

from ..handlers.shared import ServiceError
from ..registry.runtime import dispatch
from .config import BridgeConfig
from .notifier import notify
from .state import StateStore

LOGGER = logging.getLogger(__name__)


def result_is_healthy(service: str, body: str) -> bool:
    """Indique si un résultat reste exploitable."""

    normalized = body.upper()
    service = service.strip().lower().replace("-", "_")
    if service == "endpoint_check":
        return "FERME/TIMEOUT" not in normalized and "ECHEC" not in normalized
    if service == "domain_audit":
        return "ECHEC" not in normalized
    return True


class MonitorEngine:
    """Exécute la surveillance périodique."""

    # Initialise le moteur de surveillance.
    def __init__(self, config: BridgeConfig, state: StateStore, dispatcher) -> None:
        self.config = config
        self.state = state
        self.dispatcher = dispatcher

    # Exécute un cycle de surveillance.
    async def run_cycle(self, force: bool = False) -> int:
        checked = 0
        now = time.time()
        for monitor in self.state.due_monitors(now, force=force):
            checked += 1
            try:
                result = await asyncio.to_thread(
                    dispatch,
                    str(monitor.get("service", "endpoint_check")),
                    str(monitor.get("argument", "")),
                    self.config,
                    self.state,
                )
                title = result.title
                body = result.body
                status = "OK" if result_is_healthy(str(monitor.get("service", "")), body) else "ERR"
            except ServiceError as exc:
                status = "ERR"
                title = str(monitor.get("name") or "Monitor")
                body = str(exc)
            except Exception as exc:
                LOGGER.exception("Erreur monitor")
                status = "ERR"
                title = str(monitor.get("name") or "Monitor")
                body = f"{type(exc).__name__}: {exc}"

            previous, updated = self.state.update_monitor(
                int(monitor["id"]), status, title, body, now
            )
            if updated is None or previous == status or (previous == "NEW" and status == "OK"):
                continue

            name = str(updated.get("name") or title)
            if status == "ERR":
                alert_title = f"ALERTE: {name}"
                alert_body = body
            else:
                alert_title = f"RETABLI: {name}"
                alert_body = body
            self.state.add_alert(alert_title, alert_body)
            if self.config.desktop_notifications:
                await asyncio.to_thread(notify, alert_title, alert_body)
            await self.dispatcher.send_alert(alert_title, alert_body)
        return checked

    # Maintient le service actif.
    async def run_forever(self) -> None:
        await asyncio.sleep(3)
        while True:
            try:
                await self.run_cycle()
            except asyncio.CancelledError:
                raise
            except Exception:
                LOGGER.exception("Cycle monitor interrompu")
            await asyncio.sleep(max(15.0, self.config.monitor_poll_seconds))
