"""Définit les erreurs structurées FNERR1."""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import StrEnum


class ErrorCode(StrEnum):
    """Énumère les codes d erreur publics."""

    ALERT = "ALERT"
    CAPABILITY_INVALID = "CAPABILITY_INVALID"
    CONFIG_INVALID = "CONFIG_INVALID"
    INTERNAL_ERROR = "INTERNAL_ERROR"
    PERMISSION_DENIED = "PERMISSION_DENIED"
    PLUGIN_ERROR = "PLUGIN_ERROR"
    REQUEST_TIMEOUT = "REQUEST_TIMEOUT"
    SERVICE_ERROR = "SERVICE_ERROR"
    SERVICE_NOT_FOUND = "SERVICE_NOT_FOUND"


@dataclass(frozen=True, slots=True)
class ErrorEnvelope:
    """Décrit une erreur transportable."""

    code: str
    category: str
    service: str
    recoverable: bool
    message_en: str
    message_fr: str


_CODE = re.compile(r"[A-Z0-9_]{1,40}")
_CATEGORY = re.compile(r"[a-z0-9_]{1,24}")
_SERVICE = re.compile(r"[a-z0-9_]{0,32}")


# Encode un champ réservé.
def encode_field(value: str) -> str:
    return (
        (value or "")
        .replace("%", "%25")
        .replace("|", "%7C")
        .replace("\r", "%0D")
        .replace("\n", "%0A")
    )


# Décode un champ réservé.
def decode_field(value: str) -> str:
    result = value or ""
    for encoded, decoded in (
        ("%0A", "\n"),
        ("%0D", "\r"),
        ("%7C", "|"),
        ("%25", "%"),
    ):
        result = result.replace(encoded, decoded)
    return result


# Construit une erreur structurée.
def build_error(
    code: str | ErrorCode,
    category: str,
    service: str,
    recoverable: bool,
    message_en: str,
    message_fr: str,
) -> str:
    code_value = str(code)
    if _CODE.fullmatch(code_value) is None:
        raise ValueError("Code d erreur invalide")
    if _CATEGORY.fullmatch(category) is None:
        raise ValueError("Categorie d erreur invalide")
    if _SERVICE.fullmatch(service) is None:
        raise ValueError("Service d erreur invalide")
    fields = (
        "FNERR1",
        code_value,
        category,
        service,
        "1" if recoverable else "0",
        encode_field(message_en),
        encode_field(message_fr),
    )
    return "|".join(fields)


# Analyse une erreur structurée.
def parse_error(payload: str) -> ErrorEnvelope:
    fields = (payload or "").split("|")
    if len(fields) != 7 or fields[0] != "FNERR1":
        raise ValueError("Erreur FNERR1 invalide")
    code = fields[1]
    category = fields[2]
    service = fields[3]
    if _CODE.fullmatch(code) is None:
        raise ValueError("Code d erreur invalide")
    if _CATEGORY.fullmatch(category) is None:
        raise ValueError("Categorie d erreur invalide")
    if _SERVICE.fullmatch(service) is None:
        raise ValueError("Service d erreur invalide")
    if fields[4] not in {"0", "1"}:
        raise ValueError("Etat d erreur invalide")
    return ErrorEnvelope(
        code=code,
        category=category,
        service=service,
        recoverable=fields[4] == "1",
        message_en=decode_field(fields[5]),
        message_fr=decode_field(fields[6]),
    )


# Classe une erreur de fonctionnalité.
def classify_service_error(message: str) -> tuple[ErrorCode, str]:
    value = (message or "").lower()
    if "permission" in value or "liste blanche" in value:
        return ErrorCode.PERMISSION_DENIED, "permission"
    if "plugin" in value or "module" in value or "paquet" in value:
        return ErrorCode.PLUGIN_ERROR, "plugin"
    if "configuration" in value or "schema" in value:
        return ErrorCode.CONFIG_INVALID, "config"
    if "fncap2" in value or "catalogue" in value:
        return ErrorCode.CAPABILITY_INVALID, "capability"
    if "service inconnu" in value or "service incompatible" in value:
        return ErrorCode.SERVICE_NOT_FOUND, "service"
    return ErrorCode.SERVICE_ERROR, "service"
