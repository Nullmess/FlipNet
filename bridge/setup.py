"""Expose la configuration du paquet Python."""

from pathlib import Path

from setuptools import setup

PACKAGE_ROOT = Path(__file__).resolve().parent
PROJECT_VERSION = PACKAGE_ROOT.parent / "VERSION"
PACKAGE_VERSION = PACKAGE_ROOT / "VERSION"
VERSION_PATH = PROJECT_VERSION if PROJECT_VERSION.is_file() else PACKAGE_VERSION
setup(version=VERSION_PATH.read_text(encoding="utf-8").strip())
