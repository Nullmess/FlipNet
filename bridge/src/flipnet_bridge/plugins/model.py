"""Décrit les paquets externes installés."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class InstalledPackage:
    """Décrit un paquet externe valide."""

    package_id: str
    version: str
    title: str
    title_fr: str
    path: Path
    enabled: bool
    services: tuple[str, ...]
    dependencies: tuple[str, ...]
    platforms: tuple[str, ...]
    checksum: str
    issues: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class PackageAudit:
    """Décrit l’état lisible d’un paquet externe."""

    package_id: str
    path: Path
    valid: bool
    enabled: bool
    version: str = ""
    title: str = ""
    issues: tuple[str, ...] = ()
