"""Décrit le contrat public de FlipNet."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass

from .. import __version__
from .config_migration import CURRENT_CONFIG_SCHEMA
from .data_store import SCHEMA_VERSION as DATA_SCHEMA_VERSION

FEATURE_SCHEMA_VERSION = 3
PLUGIN_SCHEMA_VERSION = 2
RELEASE_MANIFEST_SCHEMA_VERSION = 1


@dataclass(frozen=True, slots=True)
class CompatibilityContract:
    """Décrit les formats actifs."""

    flipnet_version: str
    transport_protocol: str
    capability_protocol: str
    error_protocol: str
    config_schema: int
    feature_schema: int
    plugin_schema: int
    data_schema: int
    release_manifest_schema: int
    legacy_formats: tuple[str, ...]


# Retourne le contrat public actif.
def current_contract() -> CompatibilityContract:
    return CompatibilityContract(
        flipnet_version=__version__,
        transport_protocol="FN2",
        capability_protocol="FNCAP2",
        error_protocol="FNERR1",
        config_schema=CURRENT_CONFIG_SCHEMA,
        feature_schema=FEATURE_SCHEMA_VERSION,
        plugin_schema=PLUGIN_SCHEMA_VERSION,
        data_schema=DATA_SCHEMA_VERSION,
        release_manifest_schema=RELEASE_MANIFEST_SCHEMA_VERSION,
        legacy_formats=(),
    )


# Formate le contrat public.
def render_contract(contract: CompatibilityContract, *, as_json: bool = False) -> str:
    payload = asdict(contract)
    if as_json:
        return json.dumps(payload, indent=2, sort_keys=True)
    rows = [f"FlipNet compatibility {contract.flipnet_version}"]
    rows.extend(
        (
            f"Transport: {contract.transport_protocol}",
            f"Capabilities: {contract.capability_protocol}",
            f"Errors: {contract.error_protocol}",
            f"Config schema: {contract.config_schema}",
            f"Feature schema: {contract.feature_schema}",
            f"Plugin schema: {contract.plugin_schema}",
            f"Data schema: {contract.data_schema}",
            f"Release manifest schema: {contract.release_manifest_schema}",
            "Legacy formats: none",
        )
    )
    return "\n".join(rows)
