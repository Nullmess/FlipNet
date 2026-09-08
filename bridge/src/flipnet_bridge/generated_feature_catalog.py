"""Conserve l’ancien chemin d’import."""

import sys

from .registry import generated as _target

sys.modules[__name__] = _target
