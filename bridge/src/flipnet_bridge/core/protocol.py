"""Gère le protocole FN2."""

from __future__ import annotations

import re
import zlib
from dataclasses import dataclass
from enum import StrEnum

VERSION = "FN2"
MAX_SERVICE = 32
MAX_ARGUMENT_BYTES = 192
MAX_TITLE_BYTES = 64
MAX_BODY_BYTES = 1024
MAX_LINE_BYTES = 2304
CAPABILITIES = ("hello", "progress", "event", "cancel", "priority", "crc32")


class ProtocolError(ValueError):
    """Signale une trame FN2 invalide."""


class Status(StrEnum):
    """Énumère les états du protocole."""

    OK = "OK"
    ERROR = "ERR"


@dataclass(frozen=True, slots=True)
class Request:
    """Décrit une requête FN2."""

    request_id: int
    service: str
    argument: str
    session_id: int = 0
    priority: int = 1


def _decode_hex(value: str, limit: int, field: str) -> str:
    """Décode une valeur hexadécimale."""

    if (len(value) % 2) != 0 or re.fullmatch(r"[0-9A-Fa-f]*", value) is None:
        raise ProtocolError(f"invalid hexadecimal {field}")
    raw = bytes.fromhex(value)
    if len(raw) > limit:
        raise ProtocolError(f"{field} is too large")
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ProtocolError(f"{field} is not UTF-8") from exc


def _encode_hex(value: str, limit: int) -> str:
    """Encode une valeur hexadécimale."""

    raw = value.encode("utf-8")[:limit]
    while raw:
        try:
            raw.decode("utf-8")
            break
        except UnicodeDecodeError:
            raw = raw[:-1]
    return raw.hex().upper()


def _uint32(value: str, field: str) -> int:
    """Valide un entier non signé sur 32 bits."""

    if not value.isascii() or not value.isdecimal():
        raise ProtocolError(f"invalid {field}")
    result = int(value)
    if result > 0xFFFFFFFF:
        raise ProtocolError(f"{field} is out of range")
    return result


def _crc(payload: str) -> str:
    """Calcule la somme CRC32 de la trame."""

    return f"{zlib.crc32(payload.encode('ascii')) & 0xFFFFFFFF:08X}"


def parse_request(line: str) -> Request:
    """Analyse une requête FN2."""

    try:
        encoded = line.encode("ascii")
    except UnicodeEncodeError as exc:
        raise ProtocolError("frame is not ASCII") from exc
    if len(encoded) >= MAX_LINE_BYTES:
        raise ProtocolError("line is too large")
    parts = line.rstrip("\r\n").split("|")
    if len(parts) != 8 or parts[:2] != [VERSION, "REQ"]:
        raise ProtocolError("unsupported frame")
    payload = "|".join(parts[:-1])
    if parts[-1] != _crc(payload):
        raise ProtocolError("checksum mismatch")
    session_id = _uint32(parts[2], "session id")
    request_id = _uint32(parts[3], "request id")
    priority = int(parts[4]) if parts[4].isdigit() else -1
    if priority not in range(4):
        raise ProtocolError("invalid priority")
    service = parts[5]
    if re.fullmatch(r"[a-z0-9_]{1,32}", service) is None:
        raise ProtocolError("invalid service name")
    return Request(
        request_id=request_id,
        service=service,
        argument=_decode_hex(parts[6], MAX_ARGUMENT_BYTES, "argument"),
        session_id=session_id,
        priority=priority,
    )


def build_response(
    request_id: int,
    status: Status,
    title: str,
    body: str,
    *,
    session_id: int = 0,
) -> bytes:
    """Construit une réponse FN2."""

    title_hex = _encode_hex(title, MAX_TITLE_BYTES)
    body_hex = _encode_hex(body, MAX_BODY_BYTES)
    payload = f"FN2|RES|{session_id}|{request_id}|{status.value}|{title_hex}|{body_hex}"
    encoded = f"{payload}|{_crc(payload)}\n".encode("ascii")
    if len(encoded) >= MAX_LINE_BYTES:
        raise ProtocolError("encoded response is too large")
    return encoded
