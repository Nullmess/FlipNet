"""Gère les modules externes installés."""

from .manifest import PACKAGE_SCHEMA, validate_manifest
from .package import (
    configure_package,
    install_package,
    list_packages,
    remove_package,
    set_enabled,
    update_package,
)

__all__ = [
    "PACKAGE_SCHEMA",
    "configure_package",
    "install_package",
    "list_packages",
    "remove_package",
    "set_enabled",
    "update_package",
    "validate_manifest",
]
