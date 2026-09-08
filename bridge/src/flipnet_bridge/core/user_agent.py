"""Construit les identifiants HTTP."""

from .. import __version__

BRIDGE_USER_AGENT = f"FlipNet-Bridge/{__version__}"
WEB_BROWSER_USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    f"(KHTML, like Gecko) Chrome/124.0 Safari/537.36 FlipNet/{__version__}"
)
