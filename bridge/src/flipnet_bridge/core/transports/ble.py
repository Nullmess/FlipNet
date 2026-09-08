"""Gère le transport Bluetooth LE."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Callable

from bleak import BleakClient, BleakScanner
from bleak.backends.device import BLEDevice

from ..dispatcher import RequestDispatcher

LOGGER = logging.getLogger(__name__)

SERIAL_SERVICE_UUID = "8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000"
FLIPPER_TO_HOST_UUID = "19ed82ae-ed21-4c9d-4145-228e61fe0000"
HOST_TO_FLIPPER_UUID = "19ed82ae-ed21-4c9d-4145-228e62fe0000"

# Fixe la taille BLE avant la négociation.
BLE_SAFE_WRITE_CHUNK = 20
BLE_INTER_CHUNK_DELAY = 0.008
BLE_LINK_FRAME = b"FN2|LINK|UP\n"
BLE_LINK_INTERVAL_SECONDS = 2.0


# Gère le transport Bluetooth LE.
class BleTransport:
    # Initialise le transport Bluetooth LE.
    def __init__(
        self,
        dispatcher: RequestDispatcher,
        device_address: str | None = None,
        scan_timeout: float = 8.0,
    ) -> None:
        self.dispatcher = dispatcher
        self.device_address = device_address
        self.scan_timeout = scan_timeout
        self._client: BleakClient | None = None
        self._rx_buffer = bytearray()
        self._write_lock = asyncio.Lock()
        self._disconnected = asyncio.Event()
        self._loop: asyncio.AbstractEventLoop | None = None

    # Recherche le Flipper en Bluetooth.
    async def find_device(self) -> BLEDevice:
        LOGGER.info("BLE: recherche d'un Flipper executant FlipNet...")
        discovered = await BleakScanner.discover(timeout=self.scan_timeout, return_adv=True)

        named_candidates: list[BLEDevice] = []
        for device, advertisement in discovered.values():
            if self.device_address and device.address.lower() == self.device_address.lower():
                return device

            service_uuids = {uuid.lower() for uuid in (advertisement.service_uuids or [])}
            if SERIAL_SERVICE_UUID in service_uuids:
                LOGGER.info(
                    "BLE: Flipper trouve: %s (%s)", device.name or "sans nom", device.address
                )
                return device

            visible_name = (advertisement.local_name or device.name or "").lower()
            if (
                "flipper" in visible_name
                or visible_name.startswith("fz")
                or visible_name.startswith("flip_")
            ):
                named_candidates.append(device)

        if self.device_address:
            raise RuntimeError(f"BLE: appareil introuvable: {self.device_address}")
        if len(named_candidates) == 1:
            device = named_candidates[0]
            LOGGER.info(
                "BLE: UUID absent de l'annonce; tentative avec %s (%s)",
                device.name or "Flipper",
                device.address,
            )
            return device
        if len(named_candidates) > 1:
            raise RuntimeError("BLE: plusieurs Flipper visibles; utilise --device ADRESSE")
        raise RuntimeError("BLE: aucun service FlipNet detecte")

    # Traite la perte de la liaison BLE.
    def _on_disconnect(self, _client: BleakClient) -> None:
        LOGGER.warning("BLE: Flipper deconnecte")
        if self._loop:
            self._loop.call_soon_threadsafe(self._disconnected.set)

    # Traite les octets reçus par BLE.
    def _notification_callback(self, _sender: object, data: bytearray) -> None:
        self._rx_buffer.extend(data)
        while b"\n" in self._rx_buffer:
            raw_line, _, remainder = self._rx_buffer.partition(b"\n")
            self._rx_buffer = bytearray(remainder)
            try:
                line = raw_line.decode("ascii")
            except UnicodeDecodeError:
                LOGGER.warning("BLE: trame non ASCII ignoree")
                continue
            if self._loop:
                self._loop.call_soon_threadsafe(
                    lambda current_line=line: asyncio.create_task(
                        self.dispatcher.process_line(current_line, self.send, "BLE")
                    )
                )

    # Envoie les données préparées.
    async def send(self, payload: bytes) -> None:
        client = self._client
        if not client or not client.is_connected:
            raise RuntimeError("BLE: Flipper non connecte")

        async with self._write_lock:
            for offset in range(0, len(payload), BLE_SAFE_WRITE_CHUNK):
                chunk = payload[offset : offset + BLE_SAFE_WRITE_CHUNK]
                LOGGER.debug(
                    "BLE TX chunk %d-%d/%d",
                    offset,
                    offset + len(chunk),
                    len(payload),
                )
                await client.write_gatt_char(HOST_TO_FLIPPER_UUID, chunk, response=False)
                await asyncio.sleep(BLE_INTER_CHUNK_DELAY)

    # Envoie le signal de présence BLE.
    async def _link_heartbeat(self) -> None:
        while not self._disconnected.is_set():
            await self.send(BLE_LINK_FRAME)
            LOGGER.debug("BLE: handshake de liaison envoye")
            try:
                await asyncio.wait_for(
                    self._disconnected.wait(),
                    timeout=BLE_LINK_INTERVAL_SECONDS,
                )
            except TimeoutError:
                continue

    # Exécute un cycle Bluetooth LE.
    async def run_once(self) -> None:
        self._loop = asyncio.get_running_loop()
        self._disconnected.clear()
        self._rx_buffer.clear()
        device = await self.find_device()

        async with BleakClient(device, disconnected_callback=self._on_disconnect) as client:
            self._client = client
            LOGGER.info("BLE: connexion etablie")

            try:
                pair: Callable[..., object] | None = getattr(client, "pair", None)
                if pair:
                    try:
                        await pair()  # type: ignore[misc]
                    except (NotImplementedError, AttributeError):
                        LOGGER.debug("BLE: appairage explicite indisponible")
                    except Exception as exc:
                        LOGGER.warning("BLE: appairage refuse: %s", exc)

                await client.start_notify(FLIPPER_TO_HOST_UUID, self._notification_callback)
                await self.dispatcher.register_sender("BLE", self.send)
                LOGGER.info("BLE: passerelle prete")
                heartbeat = asyncio.create_task(self._link_heartbeat())
                disconnected = asyncio.create_task(self._disconnected.wait())
                done, pending = await asyncio.wait(
                    {heartbeat, disconnected},
                    return_when=asyncio.FIRST_COMPLETED,
                )
                for task in pending:
                    task.cancel()
                for task in pending:
                    try:
                        await task
                    except asyncio.CancelledError:
                        pass
                if heartbeat in done:
                    await heartbeat
            finally:
                await self.dispatcher.unregister_sender("BLE", self.send)
                try:
                    await client.stop_notify(FLIPPER_TO_HOST_UUID)
                except Exception:
                    pass
                self._client = None

    # Maintient le service actif.
    async def run_forever(self) -> None:
        while True:
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                LOGGER.warning("%s", exc)
            LOGGER.info("BLE: nouvelle tentative dans 3 secondes...")
            await asyncio.sleep(3)
