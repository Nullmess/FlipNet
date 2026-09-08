"""Affiche les notifications locales."""

from __future__ import annotations

import shutil
import subprocess
import sys


def notify(title: str, body: str) -> None:
    """Affiche une notification locale."""

    try:
        if sys.platform == "darwin" and shutil.which("osascript"):
            safe_title = title.replace('"', "'")
            safe_body = body.replace('"', "'")
            subprocess.Popen(
                [
                    "osascript",
                    "-e",
                    f'display notification "{safe_body}" with title "{safe_title}"',
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            return
        if sys.platform == "win32" and shutil.which("powershell"):
            safe = (title + ": " + body).replace("'", "''")
            command = (
                "Add-Type -AssemblyName System.Windows.Forms; "
                "[System.Windows.Forms.NotifyIcon]::new() | ForEach-Object {"
                "$_.Icon=[System.Drawing.SystemIcons]::Information;"
                "$_.Visible=$true;"
                f"$_.ShowBalloonTip(5000,'FlipNet','{safe}',"
                "[System.Windows.Forms.ToolTipIcon]::Info);"
                "Start-Sleep -Seconds 6;"
                "$_.Dispose()}"
            )
            subprocess.Popen(
                [
                    "powershell",
                    "-NoProfile",
                    "-WindowStyle",
                    "Hidden",
                    "-Command",
                    command,
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            return
        if shutil.which("notify-send"):
            subprocess.Popen(
                ["notify-send", "--app-name=FlipNet", title, body],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
    except Exception:
        return
