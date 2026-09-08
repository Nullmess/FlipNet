"""Gère le transport USB CDC."""

from __future__ import annotations

import asyncio
import grp
import logging
import os
import re
import time
from pathlib import Path
from types import SimpleNamespace
from typing import Any

import serial
from serial.tools import list_ports

from ..dispatcher import RequestDispatcher

LOGGER = logging.getLogger(__name__)
FLIPPER_USB_VID = 0x0483
FLIPPER_USB_PID = 0x5740
PROTOCOL_PREFIXES = ("FN2|",)
USB_LINK_FRAME = b"FN2|LINK|UP\n"
USB_LINK_INTERVAL_SECONDS = 2.0
USB_CLI_INTERFACE = 0
USB_FLIPNET_INTERFACE = 2
SYS_TTY_ROOT = Path("/sys/class/tty")
DEV_ROOT = Path("/dev")


# Lit le numéro final d’un port.
def _numeric_suffix(value: str) -> int:
    match = re.search(r"(\d+)$", value)
    return int(match.group(1)) if match else -1


# Lit une valeur hexadécimale sysfs.
def _read_hex(path: Path) -> int | None:
    try:
        return int(path.read_text(encoding="ascii").strip(), 16)
    except (FileNotFoundError, OSError, UnicodeError, ValueError):
        return None


# Lit les informations USB directement depuis sysfs même sans udev.
def _linux_usb_metadata(device: str) -> tuple[int | None, int | None, int | None, str]:
    name = Path(device).name
    if not name.startswith("ttyACM"):
        return None, None, None, ""
    try:
        current = (SYS_TTY_ROOT / name / "device").resolve(strict=True)
    except (FileNotFoundError, OSError, RuntimeError):
        return None, None, None, ""

    interface = None
    vid = None
    pid = None
    usb_parent = ""
    for parent in (current, *current.parents):
        if interface is None:
            interface = _read_hex(parent / "bInterfaceNumber")
        parent_vid = _read_hex(parent / "idVendor")
        parent_pid = _read_hex(parent / "idProduct")
        if parent_vid is not None and parent_pid is not None:
            vid = parent_vid
            pid = parent_pid
            usb_parent = str(parent)
            break
    return interface, vid, pid, usb_parent


# Lit le numéro d’interface USB depuis sysfs.
def _linux_interface_number(device: str) -> int | None:
    return _linux_usb_metadata(device)[0]


# Retourne les métadonnées série enrichies par sysfs.
def _port_metadata(port: Any) -> tuple[int | None, int | None, int | None, str]:
    sys_interface, sys_vid, sys_pid, parent = _linux_usb_metadata(str(port.device))
    vid = getattr(port, "vid", None)
    pid = getattr(port, "pid", None)
    return sys_interface, vid if vid is not None else sys_vid, pid if pid is not None else sys_pid, parent


def _port_score(port: Any) -> tuple[int, int, str]:
    """Classe les ports USB disponibles."""

    text = " ".join(
        str(value or "")
        for value in (
            getattr(port, "hwid", ""),
            getattr(port, "location", ""),
            getattr(port, "interface", ""),
        )
    ).upper()

    dedicated = 0
    if "MI_02" in text or re.search(r":1\.2(?:\D|$)", text):
        dedicated = 1000
    elif "MI_00" in text or re.search(r":1\.0(?:\D|$)", text):
        dedicated = -1000
    else:
        interface = _linux_interface_number(str(port.device))
        if interface == USB_FLIPNET_INTERFACE:
            dedicated = 1000
        elif interface == USB_CLI_INTERFACE:
            dedicated = -1000

    return dedicated, _numeric_suffix(str(port.device)), str(port.device)


# Construit un port minimal pour les systèmes sans métadonnées pyserial.
def _plain_port(path: Path) -> SimpleNamespace:
    return SimpleNamespace(
        device=str(path),
        hwid="",
        location="",
        interface="",
        description="",
        manufacturer="",
        product="",
        vid=None,
        pid=None,
    )


# Collecte les ports série en fusionnant pyserial et /dev.
def _available_ports() -> list[Any]:
    by_device: dict[str, Any] = {}
    try:
        for port in list_ports.comports():
            by_device[str(port.device)] = port
    except (OSError, RuntimeError):
        LOGGER.debug("USB: inventaire pyserial indisponible", exc_info=True)
    try:
        for path in sorted(DEV_ROOT.glob("ttyACM*")):
            by_device.setdefault(str(path), _plain_port(path))
    except OSError:
        LOGGER.debug("USB: inventaire /dev indisponible", exc_info=True)
    return list(by_device.values())


# Indique si un port appartient au Flipper.
def _is_flipper_port(port: Any) -> bool:
    _, vid, pid, _ = _port_metadata(port)
    description = " ".join(
        str(value or "")
        for value in (
            getattr(port, "description", ""),
            getattr(port, "manufacturer", ""),
            getattr(port, "product", ""),
            getattr(port, "hwid", ""),
        )
    ).lower()
    return (vid == FLIPPER_USB_VID and pid == FLIPPER_USB_PID) or "flipper" in description


# Trouve le port USB FlipNet.
def discover_flipnet_port(explicit_port: str | None = None) -> str:
    if explicit_port:
        return explicit_port

    ports = _available_ports()
    identified = [port for port in ports if _is_flipper_port(port)]
    candidates = identified
    if not candidates:
        candidates = [port for port in ports if Path(str(port.device)).name.startswith("ttyACM")]

    if not candidates:
        raise RuntimeError("USB: aucun port Flipper detecte")

    candidates.sort(key=_port_score, reverse=True)
    LOGGER.debug(
        "USB: ports candidats: %s",
        ", ".join(str(port.device) for port in candidates),
    )

    # Préfère le canal CDC réservé à FlipNet.
    dedicated = [port for port in candidates if _port_score(port)[0] > 0]
    if dedicated:
        selected = str(dedicated[0].device)
        LOGGER.info("USB: canal FlipNet selectionne: %s", selected)
        return selected
    if len(candidates) >= 2:
        selected = str(candidates[0].device)
        LOGGER.warning(
            "USB: metadonnees CDC incompletes; utilisation du port le mieux classe: %s",
            selected,
        )
        return selected

    selected = str(candidates[0].device)
    score = _port_score(candidates[0])[0]
    if score < 0:
        raise RuntimeError(
            "USB: seul le port CLI est visible; lance la FAP puis attends le canal FlipNet"
        )

    # Alpine avec mdev peut exposer un ttyACM fonctionnel sans métadonnées udev.
    LOGGER.warning(
        "USB: port CDC unique sans metadonnees; tentative directe sur %s",
        selected,
    )
    return selected


# Retourne le groupe propriétaire du port série.
def _device_group(port: str) -> str:
    try:
        return grp.getgrgid(os.stat(port).st_gid).gr_name
    except (KeyError, OSError):
        return "dialout ou uucp"


# Gère le transport USB CDC.
class UsbTransport:
    # Initialise le transport USB CDC.
    def __init__(
        self,
        dispatcher: RequestDispatcher,
        port: str | None = None,
        baudrate: int = 115200,
    ) -> None:
        self.dispatcher = dispatcher
        self._configured_port = port
        self.port = port
        self.baudrate = baudrate
        self._serial: serial.Serial | None = None
        self._write_lock = asyncio.Lock()

    # Ouvre le port USB sélectionné.
    def _open(self) -> serial.Serial:
        port = discover_flipnet_port(self._configured_port)
        connection = serial.Serial()
        connection.port = port
        connection.baudrate = self.baudrate
        connection.timeout = 0.5
        connection.write_timeout = 3.0
        try:
            connection.exclusive = True
        except (AttributeError, TypeError, ValueError):
            pass

        # Déclenche le front DTR attendu par le Flipper.
        connection.dtr = False
        try:
            connection.open()
        except Exception as exc:
            try:
                connection.close()
            except Exception:
                pass
            message = str(exc)
            if isinstance(exc, PermissionError) or "permission denied" in message.lower():
                group = _device_group(port)
                raise RuntimeError(
                    f"USB: acces refuse a {port}; ajoute ton utilisateur au groupe {group}, "
                    "reconnecte le Flipper puis redemarre flipnet-bridge"
                ) from exc
            if "busy" in message.lower() or "resource temporarily unavailable" in message.lower():
                raise RuntimeError(
                    f"USB: {port} est deja utilise; ferme qFlipper, screen, minicom ou tout "
                    "autre programme serie"
                ) from exc
            raise

        time.sleep(0.15)
        connection.reset_input_buffer()
        connection.dtr = True
        time.sleep(0.05)
        self.port = port
        return connection

    # Écrit une trame sur le port USB.
    def _write_sync(self, payload: bytes) -> None:
        connection = self._serial
        if not connection or not connection.is_open:
            raise RuntimeError("USB: port non connecte")
        written = connection.write(payload)
        if written != len(payload):
            raise RuntimeError(f"USB: ecriture incomplete ({written}/{len(payload)})")
        connection.flush()

    # Envoie les données préparées.
    async def send(self, payload: bytes) -> None:
        async with self._write_lock:
            await asyncio.to_thread(self._write_sync, payload)

    # Exécute un cycle USB.
    async def run_once(self) -> None:
        connection = await asyncio.to_thread(self._open)
        self._serial = connection
        LOGGER.info("USB: passerelle prete sur %s", connection.port)
        await self.dispatcher.register_sender("USB", self.send)

        next_link = 0.0
        try:
            while True:
                now = time.monotonic()
                if now >= next_link:
                    await self.send(USB_LINK_FRAME)
                    next_link = now + USB_LINK_INTERVAL_SECONDS
                    LOGGER.debug("USB: handshake de liaison envoye")

                raw_line = await asyncio.to_thread(connection.readline)
                if not raw_line:
                    continue
                try:
                    line = raw_line.rstrip(b"\r\n").decode("ascii")
                except UnicodeDecodeError:
                    LOGGER.debug("USB: donnees non ASCII ignorees")
                    continue
                if not line.startswith(PROTOCOL_PREFIXES):
                    LOGGER.debug("USB: ligne hors protocole ignoree: %r", line[:80])
                    continue
                await self.dispatcher.process_line(line, self.send, "USB")
        finally:
            await self.dispatcher.unregister_sender("USB", self.send)
            self._serial = None
            try:
                connection.close()
            except Exception:
                pass

    # Maintient le service actif.
    async def run_forever(self) -> None:
        while True:
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                LOGGER.warning("USB: %s", exc)
            LOGGER.info("USB: nouvelle tentative dans 2 secondes...")
            await asyncio.sleep(2)
