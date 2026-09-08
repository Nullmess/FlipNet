"""Définit les permissions canoniques."""

from __future__ import annotations

CANONICAL_PERMISSIONS = frozenset(
    {
        "actions.secure",
        "network.dns",
        "network.http",
        "network.scan",
        "network.socket",
        "state.read",
        "state.write",
        "system.read",
        "system.write",
    }
)

DEFAULT_FEATURE_PERMISSIONS = tuple(sorted(CANONICAL_PERMISSIONS))
