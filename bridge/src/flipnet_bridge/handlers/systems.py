"""Gère les services système."""

from __future__ import annotations

from . import shared as api


# Lit la mémoire sous Linux.
def _linux_memory() -> tuple[int | None, int | None]:
    values: dict[str, int] = {}
    try:
        for line in api.Path("/proc/meminfo").read_text(encoding="ascii").splitlines():
            key, value = line.split(":", 1)
            values[key] = int(value.strip().split()[0]) * 1024
    except Exception:
        return (None, None)
    total = values.get("MemTotal")
    available = values.get("MemAvailable")
    used = total - available if total is not None and available is not None else None
    return (total, used)


# Lit la mémoire sous Windows.
def _windows_memory() -> tuple[int | None, int | None]:

    # Regroupe les mesures de mémoire.
    class MemoryStatus(api.ctypes.Structure):
        _fields_ = [
            ("length", api.ctypes.c_ulong),
            ("load", api.ctypes.c_ulong),
            ("total_phys", api.ctypes.c_ulonglong),
            ("avail_phys", api.ctypes.c_ulonglong),
            ("total_page", api.ctypes.c_ulonglong),
            ("avail_page", api.ctypes.c_ulonglong),
            ("total_virtual", api.ctypes.c_ulonglong),
            ("avail_virtual", api.ctypes.c_ulonglong),
            ("avail_extended", api.ctypes.c_ulonglong),
        ]

    try:
        status = MemoryStatus()
        status.length = api.ctypes.sizeof(MemoryStatus)
        api.ctypes.windll.kernel32.GlobalMemoryStatusEx(api.ctypes.byref(status))
        return (int(status.total_phys), int(status.total_phys - status.avail_phys))
    except Exception:
        return (None, None)


# Lit la durée de fonctionnement.
def _uptime_seconds() -> int | None:
    system = api.platform.system().lower()
    try:
        if system == "linux":
            return int(float(api.Path("/proc/uptime").read_text(encoding="ascii").split()[0]))
        if system == "windows":
            return int(api.ctypes.windll.kernel32.GetTickCount64() // 1000)
        if system == "darwin":
            output = api.subprocess.check_output(
                ["sysctl", "-n", "kern.boottime"], text=True, timeout=2
            )
            match = api.re.search("sec = (\\d+)", output)
            if match:
                return max(0, int(api.time.time()) - int(match.group(1)))
    except Exception:
        pass
    return None


# Formate une durée lisible.
def _format_duration(seconds: int | None) -> str:
    if seconds is None:
        return "?"
    days, remainder = divmod(seconds, 86400)
    hours, remainder = divmod(remainder, 3600)
    minutes = remainder // 60
    if days:
        return f"{days}j {hours}h"
    return f"{hours}h {minutes}m"


# Formate une taille en gibioctets.
def _format_gib(value: int | None) -> str:
    if value is None:
        return "?"
    return f"{value / 1024**3:.1f}G"


# Compte les lignes d’une commande.
def _command_count(command: list[str]) -> int | None:
    if api.shutil.which(command[0]) is None:
        return None
    try:
        result = api.subprocess.run(command, check=False, capture_output=True, text=True, timeout=2)
        if result.returncode != 0:
            return None
        return len([line for line in result.stdout.splitlines() if line.strip()])
    except Exception:
        return None


# Résume l’état de l’hôte.
def host_status(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del argument, config
    system = api.platform.system() or "Unknown"
    release = api.platform.release()
    hostname = api.socket.gethostname()
    uptime = _format_duration(_uptime_seconds())
    if system.lower() == "linux":
        total, used = _linux_memory()
    elif system.lower() == "windows":
        total, used = _windows_memory()
    else:
        total, used = (None, None)
    disk = api.shutil.disk_usage(api.Path.home().anchor or "/")
    disk_percent = int(disk.used / disk.total * 100) if disk.total else 0
    os_label = f"{system} {release}"
    if system.lower() == "linux":
        os_label = f"{_linux_pretty_name()} | kernel {release}"
    lines = [
        f"OS: {os_label}",
        f"Uptime: {uptime}",
        f"CPU: {api.os.cpu_count() or '?'} coeur(s)",
        f"RAM: {_format_gib(used)} / {_format_gib(total)}",
        f"Disque: {disk_percent}%",
    ]
    if system.lower() == "linux":
        selinux = api.Path("/sys/fs/selinux/enforce")
        if selinux.exists():
            try:
                lines.append(
                    "SELinux: Enforcing"
                    if selinux.read_text().strip() == "1"
                    else "SELinux: Permissive"
                )
            except Exception:
                pass
        podman = _command_count(["podman", "ps", "-q"])
        docker = _command_count(["docker", "ps", "-q"])
        if podman is not None:
            lines.append(f"Podman: {podman} actif(s)")
        elif docker is not None:
            lines.append(f"Docker: {docker} actif(s)")
    return api.ServiceResult(hostname, "\n".join(lines))


def _run_text(command: list[str], timeout: float = 5.0) -> tuple[int, str, str]:
    """Exécute une commande locale bornée."""
    if not command or api.shutil.which(command[0]) is None:
        return (127, "", "commande absente")
    try:
        result = api.subprocess.run(
            command, check=False, capture_output=True, text=True, timeout=timeout
        )
        return (result.returncode, result.stdout.strip(), result.stderr.strip())
    except api.subprocess.TimeoutExpired:
        return (124, "", "delai depasse")
    except Exception as exc:
        return (1, "", str(exc))


# Exécute une commande PowerShell bornée.
def _powershell(script: str, timeout: float = 8.0) -> tuple[int, str, str]:
    executable = api.shutil.which("powershell") or api.shutil.which("pwsh")
    if not executable:
        return (127, "", "PowerShell absent")
    return _run_text([executable, "-NoProfile", "-NonInteractive", "-Command", script], timeout)


# Lit le nom de la distribution Linux.
def _linux_pretty_name() -> str:
    path = api.Path("/etc/os-release")
    try:
        values: dict[str, str] = {}
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" not in line or line.startswith("#"):
                continue
            key, value = line.split("=", 1)
            values[key] = value.strip().strip('"')
        return values.get("PRETTY_NAME") or values.get("NAME") or "Linux"
    except Exception:
        return "Linux"


# Indique si un service Linux est actif.
def _linux_service_active(name: str) -> str:
    if api.shutil.which("systemctl"):
        code, output, _ = _run_text(["systemctl", "is-active", name], 3)
        if code == 0:
            return output or "active"
        if output:
            return output
    if api.shutil.which("rc-service"):
        code, output, error = _run_text(["rc-service", name, "status"], 3)
        text = f"{output} {error}".lower()
        if code == 0 or "started" in text:
            return "active"
        if "stopped" in text or "crashed" in text:
            return "inactive"
    return "unknown"


# Résume l’état de sécurité.
def security_status(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del argument, config
    system = api.platform.system().lower()
    lines: list[str] = []
    if system == "linux":
        lines.append(f"OS: {_linux_pretty_name()}")
        code, output, _ = _run_text(["getenforce"], 2)
        if code == 0 and output:
            lines.append(f"SELinux: {output}")
        else:
            enforce = api.Path("/sys/fs/selinux/enforce")
            try:
                lines.append(
                    "SELinux: Enforcing"
                    if enforce.read_text().strip() == "1"
                    else "SELinux: Permissive"
                )
            except Exception:
                lines.append("SELinux: Disabled")
        apparmor = api.Path("/sys/module/apparmor/parameters/enabled")
        try:
            enabled = apparmor.read_text().strip().lower().startswith("y")
            lines.append(f"AppArmor: {('Enabled' if enabled else 'Disabled')}")
        except Exception:
            pass
        try:
            fips = api.Path("/proc/sys/crypto/fips_enabled").read_text().strip() == "1"
            lines.append(f"FIPS: {('Enabled' if fips else 'Disabled')}")
        except Exception:
            pass
        audit_state = _linux_service_active("auditd")
        if audit_state != "unknown":
            lines.append(f"Auditd: {audit_state}")
        code, output, _ = _run_text(["mokutil", "--sb-state"], 3)
        if code == 0 and output:
            lines.append(f"Secure Boot: {api._short(output, 42)}")
    elif system == "windows":
        code, output, _ = _powershell(
            "$s=Get-MpComputerStatus; 'Defender=' + $s.AntivirusEnabled; 'Realtime=' + $s.RealTimeProtectionEnabled; 'Signatures=' + $s.AntivirusSignatureLastUpdated.ToString('yyyy-MM-dd')"
        )
        if code == 0 and output:
            lines.extend(output.splitlines())
        else:
            lines.append("Defender: unavailable")
        code, output, _ = _powershell(
            "try {'SecureBoot=' + (Confirm-SecureBootUEFI)} catch {'SecureBoot=unknown'}"
        )
        if code == 0 and output:
            lines.extend(output.splitlines())
    elif system == "darwin":
        code, output, _ = _run_text(["spctl", "--status"], 3)
        lines.append(f"Gatekeeper: {output or 'unknown'}")
        code, output, _ = _run_text(["fdesetup", "status"], 3)
        lines.append(f"FileVault: {output or 'unknown'}")
        code, output, _ = _run_text(["csrutil", "status"], 3)
        lines.append(f"SIP: {output or 'unknown'}")
    else:
        lines.append(f"Platform: {api.platform.system() or 'Unknown'}")
        lines.append("Security status unavailable")
    return api.ServiceResult("Security State", "\n".join(lines))


# Résume les services système.
def services_status(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del argument, config
    system = api.platform.system().lower()
    lines: list[str] = []
    if system == "linux" and api.shutil.which("systemctl"):
        _, running, _ = _run_text(
            [
                "systemctl",
                "list-units",
                "--type=service",
                "--state=running",
                "--no-legend",
                "--plain",
            ],
            5,
        )
        _, failed, _ = _run_text(
            [
                "systemctl",
                "list-units",
                "--type=service",
                "--state=failed",
                "--no-legend",
                "--plain",
            ],
            5,
        )
        running_items = [line for line in running.splitlines() if line.strip()]
        failed_items = [line for line in failed.splitlines() if line.strip()]
        lines.append("Manager: systemd")
        lines.append(f"Running: {len(running_items)}")
        lines.append(f"Failed: {len(failed_items)}")
        for item in failed_items[:4]:
            lines.append(f"! {item.split()[0]}")
    elif system == "linux" and api.shutil.which("rc-status"):
        _, all_services, _ = _run_text(["rc-status", "--all"], 5)
        _, crashed, _ = _run_text(["rc-status", "--crashed"], 5)
        active = [line for line in all_services.splitlines() if "started" in line]
        broken = [
            line.strip()
            for line in crashed.splitlines()
            if line.strip() and (not line.startswith("Runlevel"))
        ]
        lines.extend(["Manager: OpenRC", f"Running: {len(active)}", f"Crashed: {len(broken)}"])
        lines.extend(f"! {api._short(item, 40)}" for item in broken[:4])
    elif system == "windows":
        code, output, _ = _powershell(
            "$r=(Get-Service | ? Status -eq Running).Count; $b=Get-Service | ? {$_.StartType -eq 'Automatic' -and $_.Status -ne 'Running'} | select -First 4 -Expand Name; 'Running=' + $r; 'Failed=' + @($b).Count; $b"
        )
        lines.extend(output.splitlines() if code == 0 and output else ["Services: unavailable"])
    elif system == "darwin":
        code, output, _ = _run_text(["launchctl", "list"], 5)
        items = [line for line in output.splitlines()[1:] if line.strip()] if code == 0 else []
        failed = [line for line in items if line.split()[0] not in {"-", "0"}]
        lines.extend(["Manager: launchd", f"Loaded: {len(items)}", f"Failed: {len(failed)}"])
    else:
        lines.append("Service manager unavailable")
    return api.ServiceResult("System Services", "\n".join(lines))


# Gère les protections réseau.
def network_guard(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del argument, config
    system = api.platform.system().lower()
    lines: list[str] = []
    if system == "linux":
        if api.shutil.which("firewall-cmd"):
            code, output, _ = _run_text(["firewall-cmd", "--state"], 4)
            lines.append(f"Firewall: firewalld {output or 'inactive'}")
            if code == 0:
                _, zones, _ = _run_text(["firewall-cmd", "--get-active-zones"], 4)
                first = next(
                    (
                        line.strip()
                        for line in zones.splitlines()
                        if line and (not line.startswith(" "))
                    ),
                    "none",
                )
                lines.append(f"Active zone: {first}")
                _, services, _ = _run_text(["firewall-cmd", "--list-services"], 4)
                lines.append(f"Services: {api._short(services or 'none', 80)}")
        elif api.shutil.which("ufw"):
            _, output, error = _run_text(["ufw", "status"], 4)
            text = output or error or "unknown"
            lines.append(f"Firewall: UFW {api._short(text.splitlines()[0], 50)}")
        elif api.shutil.which("nft"):
            code, output, _ = _run_text(["nft", "list", "ruleset"], 4)
            count = len([line for line in output.splitlines() if line.strip().startswith("chain ")])
            lines.append(f"Firewall: nftables {('active' if code == 0 and count else 'unknown')}")
            lines.append(f"Chains: {count}")
        else:
            lines.append("Firewall: not detected")
        lines.append(f"NetworkManager: {_linux_service_active('NetworkManager')}")
    elif system == "windows":
        code, output, _ = _powershell("Get-NetFirewallProfile | % { $_.Name + '=' + $_.Enabled }")
        lines.extend(
            output.splitlines() if code == 0 and output else ["Windows Firewall: unavailable"]
        )
    elif system == "darwin":
        binary = "/usr/libexec/ApplicationFirewall/socketfilterfw"
        if api.Path(binary).exists():
            code, output, _ = _run_text([binary, "--getglobalstate"], 4)
            lines.append(output if code == 0 and output else "Application Firewall: unknown")
        code, output, error = _run_text(["pfctl", "-s", "info"], 4)
        first = (output or error).splitlines()
        lines.append(f"PF: {(api._short(first[0], 60) if first else 'unknown')}")
    else:
        lines.append("Network guard unavailable")
    return api.ServiceResult("Network Guard", "\n".join(lines))


# Résume les charges de travail locales.
def workloads_status(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del argument, config
    runtime = ""
    if api.shutil.which("podman"):
        runtime = "podman"
    elif api.shutil.which("docker"):
        runtime = "docker"
    if not runtime:
        return api.ServiceResult(
            "Workloads", "Container runtime: none\nPodman or Docker not detected"
        )
    code, running, error = _run_text([runtime, "ps", "--format", "{{.Names}}|{{.Status}}"], 6)
    if code != 0:
        return api.ServiceResult(
            "Workloads", f"Runtime: {runtime}\nUnavailable: {api._short(error, 90)}"
        )
    _, all_ids, _ = _run_text([runtime, "ps", "-a", "-q"], 6)
    running_items = [line for line in running.splitlines() if line.strip()]
    all_items = [line for line in all_ids.splitlines() if line.strip()]
    lines = [
        f"Runtime: {runtime}",
        f"Running: {len(running_items)}",
        f"Stopped: {max(0, len(all_items) - len(running_items))}",
    ]
    for item in running_items[:5]:
        name, _, status = item.partition("|")
        lines.append(f"> {api._short(name, 24)}: {api._short(status, 36)}")
    return api.ServiceResult("Workloads", "\n".join(lines))


# Compte les mises à jour proposées.
def _count_update_lines(output: str, manager: str) -> int:
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if manager == "apt":
        return len([line for line in lines if line.startswith("Inst ")])
    if manager == "dnf":
        return len(
            [
                line
                for line in lines
                if api.re.match("^[A-Za-z0-9_.+:-]+\\s+\\S+\\s+\\S+", line)
                and (not line.lower().startswith(("last metadata", "obsoleting", "security:")))
            ]
        )
    if manager == "zypper":
        return len([line for line in lines if line.startswith("v |")])
    return len(lines)


# Résume les mises à jour disponibles.
def updates_status(argument: str, config: api.BridgeConfig) -> api.ServiceResult:
    del argument, config
    system = api.platform.system().lower()
    lines: list[str] = []
    if system == "linux":
        manager = "none"
        count: int | None = None
        if api.shutil.which("dnf"):
            manager = "dnf"
            code, output, error = _run_text(["dnf", "-q", "check-update"], 12)
            if code in {0, 100}:
                count = _count_update_lines(output, manager)
            elif error:
                lines.append(f"Error: {api._short(error, 70)}")
        elif api.shutil.which("yum"):
            manager = "dnf"
            code, output, _ = _run_text(["yum", "-q", "check-update"], 12)
            if code in {0, 100}:
                count = _count_update_lines(output, manager)
        elif api.shutil.which("apt-get"):
            manager = "apt"
            code, output, _ = _run_text(["apt-get", "-s", "upgrade"], 12)
            if code == 0:
                count = _count_update_lines(output, manager)
        elif api.shutil.which("pacman"):
            manager = "pacman"
            code, output, _ = _run_text(["pacman", "-Qu"], 12)
            if code in {0, 1}:
                count = _count_update_lines(output, manager)
        elif api.shutil.which("zypper"):
            manager = "zypper"
            code, output, _ = _run_text(["zypper", "--non-interactive", "list-updates"], 12)
            if code in {0, 100}:
                count = _count_update_lines(output, manager)
        elif api.shutil.which("apk"):
            manager = "apk"
            code, output, _ = _run_text(["apk", "version", "-l", "<"], 12)
            if code == 0:
                count = _count_update_lines(output, manager)
        lines.append(f"Manager: {manager}")
        lines.append(f"Available: {(count if count is not None else '?')}")
        lines.append(
            f"Reboot: {('required' if api.Path('/var/run/reboot-required').exists() else 'no')}"
        )
    elif system == "windows":
        code, output, _ = _powershell(
            "$s=New-Object -ComObject Microsoft.Update.Session; $r=$s.CreateUpdateSearcher().Search(\"IsInstalled=0 and IsHidden=0\"); 'Available=' + $r.Updates.Count; 'Reboot=' + (Test-Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WindowsUpdate\\Auto Update\\RebootRequired')",
            15,
        )
        lines.extend(
            output.splitlines() if code == 0 and output else ["Windows Update: unavailable"]
        )
    elif system == "darwin":
        code, output, error = _run_text(["softwareupdate", "-l"], 15)
        text = output or error
        count = len([line for line in text.splitlines() if line.lstrip().startswith("*")])
        lines.extend(
            ["Manager: softwareupdate", f"Available: {(count if code in {0, 1} else '?')}"]
        )
    else:
        lines.append("Update status unavailable")
    return api.ServiceResult("System Updates", "\n".join(lines))
