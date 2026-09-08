"""Distribue les requêtes entre les transports."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable

from ..handlers.management import MANAGEMENT_SERVICE_IDS
from ..handlers.shared import ServiceError, _normalise
from ..registry import runtime as plugins
from ..registry.runtime import dispatch
from .config import BridgeConfig
from .error_protocol import ErrorCode, build_error, classify_service_error
from .localization import (
    localize_error,
    localize_result,
    request_config,
    split_language,
    tr,
)
from .protocol import ProtocolError, Status, build_response, parse_request
from .state import StateStore

LOGGER = logging.getLogger(__name__)
SendCallback = Callable[[bytes], Awaitable[None]]

_WIRE_PROTOCOL_PREFIXES = (
    "FNCAP2\n",
    "FNLOC1\n",
    "FNWIKI1\n",
    "FNWEB1\n",
    "FNSEL1\n",
    "FNFEED1\n",
    "FNEXT1\n",
    "FNPLUG1\n",
)


def _is_wire_body(value: str) -> bool:
    """Détecte un corps de protocole structuré."""

    return (value or "").replace("\r", "").startswith(_WIRE_PROTOCOL_PREFIXES)


def _normalise_wire_body(value: str, ascii_only: bool) -> str:
    """Préserve les séparateurs des protocoles structurés."""

    text = (value or "").replace("\r", "")
    if _is_wire_body(text):
        return text
    return _normalise(text, ascii_only)


class RequestDispatcher:
    """Distribue les requêtes entre les transports."""

    # Initialise distribue les requêtes entre les transports.
    def __init__(self, config: BridgeConfig, state: StateStore) -> None:
        self.config = config
        self.state = state
        self._senders: dict[str, SendCallback] = {}
        self._senders_lock = asyncio.Lock()

    # Enregistre un transport de sortie.
    async def register_sender(self, name: str, send: SendCallback) -> None:
        async with self._senders_lock:
            self._senders[name] = send

    # Retire un transport de sortie.
    async def unregister_sender(self, name: str, send: SendCallback) -> None:
        async with self._senders_lock:
            if self._senders.get(name) == send:
                self._senders.pop(name, None)

    # Envoie une alerte sur tous les transports.
    async def send_alert(self, title: str, body: str) -> None:
        safe_title = _normalise(title, self.config.ascii_only)
        safe_body = _normalise(body, self.config.ascii_only)
        error_body = build_error(
            ErrorCode.ALERT,
            "event",
            "",
            True,
            safe_body,
            safe_body,
        )
        payload = build_response(0, Status.ERROR, safe_title, error_body)
        async with self._senders_lock:
            senders = dict(self._senders)
        for name in ("USB", "BLE"):
            send = senders.get(name)
            if send is None:
                continue
            try:
                await send(payload)
                LOGGER.info("Alerte envoyée via %s: %s", name, safe_title)
                return
            except Exception:
                LOGGER.exception("Alerte impossible via %s", name)

    # Analyse puis exécute une requête reçue.
    async def process_line(
        self,
        line: str,
        send: SendCallback,
        transport_name: str,
    ) -> None:
        LOGGER.debug("RX %s: %s", transport_name, line)
        try:
            request = parse_request(line)
        except ProtocolError as exc:
            LOGGER.warning("Requete %s invalide: %s", transport_name, exc)
            return

        LOGGER.info(
            "Requete %s via %s: %s(%r)",
            request.request_id,
            transport_name,
            request.service,
            request.argument,
        )
        language, effective_argument = split_language(request.argument, self.config.language)
        localized_config = (
            self.config
            if request.service in MANAGEMENT_SERVICE_IDS
            else request_config(self.config, language)
        )
        status = Status.OK
        wire_body = ""
        try:
            timeout = plugins.REGISTRY.timeout_for(request.service, localized_config)
            result = await asyncio.wait_for(
                asyncio.to_thread(
                    dispatch,
                    request.service,
                    effective_argument,
                    localized_config,
                    self.state,
                ),
                timeout=timeout,
            )
            history_title, history_body = localize_result(result.title, result.body, language)
            wire_body = history_body
        except TimeoutError:
            status = Status.ERROR
            history_title = tr(language, "Timeout", "Delai depasse")
            message_en = "The feature exceeded its execution timeout"
            message_fr = "La fonctionnalite a depasse son delai d execution"
            history_body = tr(language, message_en, message_fr)
            wire_body = build_error(
                ErrorCode.REQUEST_TIMEOUT,
                "runtime",
                request.service,
                True,
                message_en,
                message_fr,
            )
        except ServiceError as exc:
            status = Status.ERROR
            history_title = tr(language, "Error", "Erreur")
            message_fr = str(exc)
            message_en = localize_error(message_fr, "en")
            history_body = message_fr if language == "fr" else message_en
            code, category = classify_service_error(message_fr)
            wire_body = build_error(
                code,
                category,
                request.service,
                True,
                message_en,
                message_fr,
            )
        except Exception:  # Maintient le transport tant que la requête avance.
            LOGGER.exception("Unexpected service error")
            status = Status.ERROR
            history_title = tr(language, "Internal error", "Erreur interne")
            message_en = "The service failed unexpectedly"
            message_fr = "Le service a echoue de maniere inattendue"
            history_body = tr(language, message_en, message_fr)
            wire_body = build_error(
                ErrorCode.INTERNAL_ERROR,
                "runtime",
                request.service,
                False,
                message_en,
                message_fr,
            )

        preserve_wire_body = status == Status.OK and _is_wire_body(wire_body)
        history_title = _normalise(history_title, self.config.ascii_only)
        history_body = _normalise(history_body, self.config.ascii_only)
        if status == Status.OK and not preserve_wire_body:
            wire_body = history_body
        response = build_response(
            request.request_id,
            status,
            history_title,
            _normalise_wire_body(wire_body, self.config.ascii_only),
            session_id=request.session_id,
        )

        if request.service not in {
            "capabilities",
            "dashboard",
            "app_search",
            "extension_manager_list",
            "plugin_manager_list",
        }:
            self.state.add_history(
                request.service,
                effective_argument,
                status.value,
                history_title,
                history_body,
            )

        try:
            await send(response)
            LOGGER.debug("TX %s: %s", transport_name, response.decode("ascii").rstrip())
        except Exception:
            LOGGER.exception("Impossible d'envoyer la reponse via %s", transport_name)
