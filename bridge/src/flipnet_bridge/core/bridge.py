"""Coordonne les transports de la passerelle."""

from __future__ import annotations

import asyncio

from ..registry.runtime import configure_installed_plugins
from .config import BridgeConfig
from .dispatcher import RequestDispatcher
from .monitor import MonitorEngine
from .platform_paths import default_plugins_path
from .state import StateStore
from .transports.ble import BLE_SAFE_WRITE_CHUNK, BleTransport
from .transports.usb import UsbTransport


class FlipNetBridge:
    """Coordonne les transports et la surveillance."""

    # Initialise coordonne les transports et la surveillance.
    def __init__(
        self,
        config: BridgeConfig,
        transport: str = "usb",
        device_address: str | None = None,
        usb_port: str | None = None,
    ) -> None:
        if transport not in {"auto", "ble", "usb"}:
            raise ValueError(f"unsupported transport: {transport}")
        if config.state_path is None:
            raise ValueError("config.state_path is required")
        if config.data_path is None:
            raise ValueError("config.data_path is required")
        configure_installed_plugins(default_plugins_path())
        self.transport = transport
        self.state = StateStore(config.state_path, config.data_path)
        self.dispatcher = RequestDispatcher(config, self.state)
        self.monitor = MonitorEngine(config, self.state, self.dispatcher)
        self.ble = BleTransport(self.dispatcher, device_address=device_address)
        self.usb = UsbTransport(self.dispatcher, port=usb_port)

    # Retourne les transports sélectionnés.
    def _selected(self):
        if self.transport == "ble":
            return [self.ble]
        if self.transport == "usb":
            return [self.usb]
        return [self.usb, self.ble]

    # Exécute un cycle de la passerelle.
    async def run_once(self) -> None:
        transports = self._selected()
        if len(transports) == 1:
            await transports[0].run_once()
            return
        async with asyncio.TaskGroup() as group:
            for transport in transports:
                group.create_task(transport.run_once())

    # Maintient le service actif.
    async def run_forever(self) -> None:
        async with asyncio.TaskGroup() as group:
            group.create_task(self.monitor.run_forever())
            for transport in self._selected():
                group.create_task(transport.run_forever())


__all__ = ["BLE_SAFE_WRITE_CHUNK", "BleTransport", "FlipNetBridge", "UsbTransport"]
