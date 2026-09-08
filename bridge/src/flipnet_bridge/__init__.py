"""Expose le paquet FlipNet."""

from __future__ import annotations

from importlib.metadata import PackageNotFoundError, version
from pathlib import Path


# Lit la version racine du projet.
def _project_version() -> str:
    source_version = Path(__file__).resolve().parents[3] / "VERSION"
    if source_version.is_file():
        return source_version.read_text(encoding="utf-8").strip()
    try:
        return version("flipnet-bridge")
    except PackageNotFoundError:
        return "0.0.0"


__version__ = _project_version()
