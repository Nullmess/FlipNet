"""Définit les chemins selon le système."""

from __future__ import annotations

import os
import sys
from pathlib import Path


# Retourne le chemin de configuration.
def default_config_path() -> Path:
    if sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
        return base / "FlipNet" / "config.json"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "FlipNet" / "config.json"
    base = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return base / "flipnet" / "config.json"


# Retourne le chemin des journaux.
def default_log_path() -> Path:
    if sys.platform == "win32":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
        return base / "FlipNet" / "logs" / "bridge.log"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Logs" / "FlipNet" / "bridge.log"
    base = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local" / "state"))
    return base / "flipnet" / "bridge.log"


def default_state_path() -> Path:
    """Retourne le chemin de l’état."""

    if sys.platform == "win32":
        base = Path(os.environ.get("LOCALAPPDATA", Path.home() / "AppData" / "Local"))
        return base / "FlipNet" / "state.json"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "FlipNet" / "state.json"
    base = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local" / "state"))
    return base / "flipnet" / "state.json"


def default_data_path() -> Path:
    """Retourne le chemin des données."""

    if sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
        return base / "FlipNet" / "data.json"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "FlipNet" / "data.json"
    base = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return base / "flipnet" / "data.json"


def default_plugins_path() -> Path:
    """Retourne le dossier des extensions."""
    if sys.platform == "win32":
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
        return base / "FlipNet" / "plugins"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "FlipNet" / "plugins"
    base = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share"))
    return base / "flipnet" / "plugins"
