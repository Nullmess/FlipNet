#!/usr/bin/env sh
# Désinstalle la passerelle sur macOS.
set -eu
PLIST="$HOME/Library/LaunchAgents/com.flipnet.bridge.plist"
launchctl bootout "gui/$(id -u)/com.flipnet.bridge" 2>/dev/null || true
rm -f "$PLIST"
rm -rf "$HOME/Library/Application Support/FlipNet/venv"
echo "FlipNet Bridge desinstalle. Les journaux et la configuration ont ete conserves."
