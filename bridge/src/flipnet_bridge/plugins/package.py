"""Gère le cycle de vie des paquets externes."""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import stat
import tempfile
import zipfile
from collections.abc import Iterable
from pathlib import Path
from typing import Any

from ..registry.generated import BUILTIN_MANIFESTS
from .manifest import (
    MAX_PACKAGE_SERVICES,
    bridge_compatibility_issues,
    configuration_defaults,
    configuration_keys,
    dependency_issues,
    iter_service_ids,
    normalise_package_id,
    parse_semver,
    redact_configuration,
    required_configuration_issues,
    validate_manifest,
)
from .model import InstalledPackage, PackageAudit

MAX_PACKAGE_BYTES = 2_000_000
MAX_ARCHIVE_BYTES = 4_000_000
MAX_ARCHIVE_ENTRY_BYTES = 1_000_000
MAX_ARCHIVE_ENTRIES = 16
MAX_INSTALLED_SERVICES = MAX_PACKAGE_SERVICES
_ALLOWED_FILES = {
    "manifest.json",
    "README.md",
    "LICENSE",
    "LICENSE.txt",
    "icon.png",
}


def _read_json(path: Path) -> dict[str, Any]:
    """Lit un objet JSON depuis le disque."""

    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("Le fichier JSON doit contenir un objet")
    return raw


def _write_json(path: Path, payload: dict[str, Any], mode: int = 0o644) -> None:
    """Écrit un objet JSON de façon atomique."""

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.chmod(temporary, mode)
    temporary.replace(path)


def package_checksum(path: Path) -> str:
    """Calcule l’empreinte SHA-256 du paquet."""

    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(131072), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _archive_members(archive: zipfile.ZipFile) -> tuple[zipfile.ZipInfo, ...]:
    """Valide les fichiers contenus dans une archive."""

    members = tuple(info for info in archive.infolist() if not info.is_dir())
    if not members or len(members) > MAX_ARCHIVE_ENTRIES:
        raise ValueError("Nombre de fichiers du paquet invalide")
    names: set[str] = set()
    total = 0
    for info in members:
        candidate = Path(info.filename)
        if (
            candidate.is_absolute()
            or ".." in candidate.parts
            or len(candidate.parts) != 1
            or info.filename not in _ALLOWED_FILES
        ):
            raise ValueError("Chemin interdit dans le paquet")
        if info.filename in names:
            raise ValueError("Fichier dupliqué dans le paquet")
        names.add(info.filename)
        mode = info.external_attr >> 16
        if stat.S_ISLNK(mode) or info.flag_bits & 0x1:
            raise ValueError("Fichier spécial interdit dans le paquet")
        if info.file_size > MAX_ARCHIVE_ENTRY_BYTES:
            raise ValueError("Fichier du paquet trop volumineux")
        total += info.file_size
        if total > MAX_ARCHIVE_BYTES:
            raise ValueError("Contenu du paquet trop volumineux")
    if "manifest.json" not in names:
        raise ValueError("Le fichier manifest.json est absent")
    return members


def validate_package(path: Path) -> dict[str, Any]:
    """Valide une archive externe sans l’installer."""

    if not path.is_file() or path.stat().st_size > MAX_PACKAGE_BYTES:
        raise ValueError("Paquet absent ou trop volumineux")
    if path.suffix != ".flipnet-plugin" or not zipfile.is_zipfile(path):
        raise ValueError("Le paquet doit être une archive flipnet-plugin")
    with zipfile.ZipFile(path) as archive:
        _archive_members(archive)
        try:
            raw = json.loads(archive.read("manifest.json").decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("Manifeste JSON invalide") from exc
    return validate_manifest(raw)


def _enabled(path: Path) -> bool:
    """Lit l’état actif d’un paquet."""

    state_path = path / "state.json"
    if state_path.is_file():
        return bool(_read_json(state_path).get("enabled", True))
    legacy = path / "enabled"
    if legacy.is_file():
        return legacy.read_text(encoding="ascii").strip() != "0"
    return True


def _configuration(path: Path, manifest: dict[str, Any]) -> dict[str, object]:
    """Charge les réglages connus d’un paquet."""

    values = configuration_defaults(manifest)
    config_path = path / "config.json"
    if not config_path.is_file():
        return values
    raw = _read_json(config_path)
    for key in configuration_keys(manifest):
        if key in raw:
            values[key] = raw[key]
    return _coerce_configuration(manifest, values, require_complete=False)


def _field_map(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Indexe les réglages déclarés."""

    return {item["key"]: item for item in manifest["configuration"]}


def _coerce_value(field: dict[str, Any], value: object) -> object:
    """Convertit une valeur selon son type."""

    field_type = field["type"]
    if field_type in {"text", "secret"}:
        return str(value or "")[: field["max_length"]]
    if field_type == "integer":
        try:
            return int(value)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"Valeur entière invalide pour {field['key']}") from exc
    if field_type == "boolean":
        if isinstance(value, bool):
            return value
        text = str(value or "").strip().lower()
        if text in {"1", "true", "yes", "on"}:
            return True
        if text in {"0", "false", "no", "off"}:
            return False
        raise ValueError(f"Valeur booléenne invalide pour {field['key']}")
    text = str(value or "")
    if text not in field["choices"]:
        raise ValueError(f"Choix invalide pour {field['key']}")
    return text


def _coerce_configuration(
    manifest: dict[str, Any],
    values: dict[str, object],
    require_complete: bool = True,
) -> dict[str, object]:
    """Valide tous les réglages d’un paquet."""

    fields = _field_map(manifest)
    unknown = set(values).difference(fields)
    if unknown:
        raise ValueError("Réglage inconnu " + sorted(unknown)[0])
    result = {
        key: _coerce_value(field, values.get(key, field["default"]))
        for key, field in fields.items()
    }
    issues = required_configuration_issues(manifest, result)
    if require_complete and issues:
        raise ValueError(issues[0])
    return result


def read_configuration(
    package_id: str,
    plugins_dir: Path,
    redact: bool = False,
) -> dict[str, object]:
    """Retourne les réglages d’un paquet installé."""

    package_id = normalise_package_id(package_id)
    path = plugins_dir / package_id
    manifest = validate_manifest(_read_json(path / "manifest.json"))
    values = _configuration(path, manifest)
    return redact_configuration(manifest, values) if redact else values


def configure_package(
    package_id: str,
    plugins_dir: Path,
    assignments: dict[str, object],
    unset: Iterable[str] = (),
) -> dict[str, object]:
    """Modifie les réglages d’un paquet installé."""

    package_id = normalise_package_id(package_id)
    path = plugins_dir / package_id
    if not (path / "manifest.json").is_file():
        raise ValueError("Paquet externe introuvable")
    manifest = validate_manifest(_read_json(path / "manifest.json"))
    values = _configuration(path, manifest)
    fields = _field_map(manifest)
    for key in unset:
        if key not in fields:
            raise ValueError(f"Réglage inconnu {key}")
        values[key] = fields[key]["default"]
    for key, value in assignments.items():
        if key not in fields:
            raise ValueError(f"Réglage inconnu {key}")
        values[key] = value
    values = _coerce_configuration(manifest, values)
    _write_json(path / "config.json", values, 0o600)
    return redact_configuration(manifest, values)


def _installed_manifests(
    plugins_dir: Path,
    exclude: str = "",
) -> dict[str, dict[str, Any]]:
    """Charge les manifestes installés et valides."""

    result = {}
    if not plugins_dir.is_dir():
        return result
    for directory in sorted(plugins_dir.iterdir()):
        if not directory.is_dir() or directory.name == exclude:
            continue
        manifest_path = directory / "manifest.json"
        if not manifest_path.is_file():
            continue
        try:
            manifest = validate_manifest(_read_json(manifest_path))
        except (OSError, ValueError, json.JSONDecodeError):
            continue
        result[manifest["id"]] = manifest
    return result


def _service_collisions(
    manifest: dict[str, Any],
    plugins_dir: Path,
    replacing: str = "",
) -> tuple[str, ...]:
    """Liste les identifiants déjà utilisés."""

    reserved = set(BUILTIN_MANIFESTS)
    for package in _installed_manifests(plugins_dir, replacing).values():
        reserved.update(iter_service_ids(package))
    return tuple(sorted(set(iter_service_ids(manifest)).intersection(reserved)))


def _dependency_map(
    plugins_dir: Path,
    candidate: dict[str, Any] | None = None,
) -> dict[str, str]:
    """Construit les versions disponibles pour les dépendances."""

    versions = {
        package_id: manifest["version"]
        for package_id, manifest in _installed_manifests(plugins_dir).items()
    }
    if candidate is not None:
        versions[candidate["id"]] = candidate["version"]
    return versions


def _dependent_issues(
    plugins_dir: Path,
    versions: dict[str, str],
    ignored: str = "",
) -> tuple[str, ...]:
    """Vérifie les dépendances des paquets actifs."""

    issues = []
    for package_id, manifest in _installed_manifests(plugins_dir).items():
        if package_id == ignored or not _enabled(plugins_dir / package_id):
            continue
        for issue in dependency_issues(manifest, versions):
            issues.append(f"{package_id}: {issue}")
    return tuple(issues)


def _package_issues(
    path: Path,
    manifest: dict[str, Any],
    versions: dict[str, str],
) -> tuple[str, ...]:
    """Évalue la compatibilité d’un paquet installé."""

    issues = list(bridge_compatibility_issues(manifest))
    issues.extend(dependency_issues(manifest, versions))
    try:
        values = _configuration(path, manifest)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        issues.append(str(exc))
    else:
        issues.extend(required_configuration_issues(manifest, values))
    return tuple(dict.fromkeys(issues))


def list_packages(plugins_dir: Path) -> tuple[InstalledPackage, ...]:
    """Liste les paquets externes installés."""

    manifests = _installed_manifests(plugins_dir)
    versions = {key: value["version"] for key, value in manifests.items()}
    result = []
    for package_id in sorted(manifests):
        manifest = manifests[package_id]
        package_id = normalise_package_id(package_id)
        path = plugins_dir / package_id
        checksum_path = path / "package.sha256"
        checksum = (
            checksum_path.read_text(encoding="ascii").strip() if checksum_path.is_file() else ""
        )
        result.append(
            InstalledPackage(
                package_id=package_id,
                version=manifest["version"],
                title=manifest["title"],
                title_fr=manifest["title_fr"],
                path=path,
                enabled=_enabled(path),
                services=tuple(item["id"] for item in manifest["services"]),
                dependencies=tuple(item["id"] for item in manifest["dependencies"]),
                platforms=tuple(manifest["platforms"]),
                checksum=checksum,
                issues=_package_issues(path, manifest, versions),
            )
        )
    return tuple(result)


def audit_packages(plugins_dir: Path) -> tuple[PackageAudit, ...]:
    """Liste aussi les paquets corrompus."""

    valid = {item.path.name: item for item in list_packages(plugins_dir)}
    audits = []
    if not plugins_dir.is_dir():
        return ()
    for directory in sorted(plugins_dir.iterdir()):
        if not directory.is_dir():
            continue
        package = valid.get(directory.name)
        if package is not None:
            audits.append(
                PackageAudit(
                    package.package_id,
                    package.path,
                    not package.issues,
                    package.enabled,
                    package.version,
                    package.title,
                    package.issues,
                )
            )
            continue
        try:
            manifest = validate_manifest(_read_json(directory / "manifest.json"))
            identifier = manifest["id"]
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            identifier = directory.name
            error = str(exc)
        else:
            error = "Paquet non indexé"
        audits.append(PackageAudit(identifier, directory, False, False, issues=(error,)))
    return tuple(audits)


def _copy_archive(
    package_path: Path,
    destination: Path,
    manifest: dict[str, Any],
) -> None:
    """Extrait les fichiers autorisés du paquet."""

    with zipfile.ZipFile(package_path) as archive:
        for info in _archive_members(archive):
            if info.filename == "manifest.json":
                continue
            target = destination / info.filename
            target.write_bytes(archive.read(info))
            os.chmod(target, 0o644)
    _write_json(destination / "manifest.json", manifest)


def _deploy(
    package_path: Path,
    plugins_dir: Path,
    manifest: dict[str, Any],
    checksum: str,
    enabled: bool,
    configuration: dict[str, object],
) -> Path:
    """Déploie un paquet avec restauration en cas d’échec."""

    plugins_dir.mkdir(parents=True, exist_ok=True)
    destination = plugins_dir / manifest["id"]
    temporary = Path(tempfile.mkdtemp(prefix=f".{manifest['id']}.new-", dir=plugins_dir))
    backup = plugins_dir / f".{manifest['id']}.backup"
    try:
        _copy_archive(package_path, temporary, manifest)
        (temporary / "package.sha256").write_text(checksum + "\n", encoding="ascii")
        os.chmod(temporary / "package.sha256", 0o644)
        _write_json(temporary / "state.json", {"enabled": enabled})
        _write_json(temporary / "config.json", configuration, 0o600)
        if backup.exists():
            shutil.rmtree(backup)
        if destination.exists():
            destination.replace(backup)
        temporary.replace(destination)
        if backup.exists():
            shutil.rmtree(backup)
    except Exception:
        if destination.exists() and backup.exists():
            shutil.rmtree(destination)
        if backup.exists():
            backup.replace(destination)
        if temporary.exists():
            shutil.rmtree(temporary)
        raise
    return destination


def _prepare_install(
    path: Path,
    plugins_dir: Path,
    expected_sha256: str,
    replacing: str = "",
) -> tuple[dict[str, Any], str]:
    """Valide un paquet avant toute écriture."""

    manifest = validate_package(path)
    checksum = package_checksum(path)
    if expected_sha256 and checksum.lower() != expected_sha256.strip().lower():
        raise ValueError("Empreinte SHA-256 du paquet incorrecte")
    compatibility = bridge_compatibility_issues(manifest)
    if compatibility:
        raise ValueError(compatibility[0])
    collisions = _service_collisions(manifest, plugins_dir, replacing)
    if collisions:
        raise ValueError("Identifiant de service déjà utilisé " + collisions[0])
    installed_count = sum(
        len(item["services"]) for item in _installed_manifests(plugins_dir, replacing).values()
    )
    if installed_count + len(manifest["services"]) > MAX_INSTALLED_SERVICES:
        raise ValueError("Limite de dix services externes dépassée")
    versions = _dependency_map(plugins_dir, manifest)
    dependencies = dependency_issues(manifest, versions)
    if dependencies:
        raise ValueError(dependencies[0])
    dependents = _dependent_issues(plugins_dir, versions, replacing)
    if dependents:
        raise ValueError(dependents[0])
    return manifest, checksum


def _installed_package(plugins_dir: Path, package_id: str) -> InstalledPackage:
    """Retourne un paquet installé par identifiant."""

    package_id = normalise_package_id(package_id)
    package = next(
        (item for item in list_packages(plugins_dir) if item.package_id == package_id), None
    )
    if package is None:
        raise ValueError("Paquet externe introuvable")
    return package


def install_package(
    path: Path,
    plugins_dir: Path,
    expected_sha256: str = "",
) -> InstalledPackage:
    """Installe un nouveau paquet externe."""

    manifest, checksum = _prepare_install(path, plugins_dir, expected_sha256)
    destination = plugins_dir / manifest["id"]
    if destination.exists():
        raise ValueError("Le paquet est déjà installé")
    configuration = _coerce_configuration(
        manifest,
        configuration_defaults(manifest),
        require_complete=False,
    )
    enabled = not required_configuration_issues(manifest, configuration)
    _deploy(path, plugins_dir, manifest, checksum, enabled, configuration)
    return _installed_package(plugins_dir, manifest["id"])


def update_package(
    path: Path,
    plugins_dir: Path,
    expected_sha256: str = "",
) -> InstalledPackage:
    """Met à jour un paquet externe existant."""

    candidate = validate_package(path)
    current = _installed_package(plugins_dir, candidate["id"])
    if parse_semver(candidate["version"]) <= parse_semver(current.version):
        raise ValueError("La mise à jour doit avoir une version supérieure")
    manifest, checksum = _prepare_install(
        path,
        plugins_dir,
        expected_sha256,
        replacing=candidate["id"],
    )
    old_manifest = validate_manifest(_read_json(current.path / "manifest.json"))
    old_values = _configuration(current.path, old_manifest)
    defaults = configuration_defaults(manifest)
    values = {key: old_values.get(key, value) for key, value in defaults.items()}
    configuration = _coerce_configuration(manifest, values, require_complete=False)
    enabled = current.enabled and not required_configuration_issues(manifest, configuration)
    _deploy(path, plugins_dir, manifest, checksum, enabled, configuration)
    return _installed_package(plugins_dir, manifest["id"])


def _enabled_dependents(package_id: str, plugins_dir: Path) -> tuple[str, ...]:
    """Liste les paquets actifs qui dépendent de la cible."""

    package_id = normalise_package_id(package_id)
    result = []
    for item in list_packages(plugins_dir):
        if item.enabled and package_id in item.dependencies:
            result.append(item.package_id)
    return tuple(result)


def set_enabled(
    package_id: str,
    plugins_dir: Path,
    enabled: bool,
    force: bool = False,
) -> None:
    """Active ou désactive un paquet externe."""

    package = _installed_package(plugins_dir, package_id)
    if enabled:
        if package.issues:
            raise ValueError(package.issues[0])
    elif not force:
        dependents = _enabled_dependents(package_id, plugins_dir)
        if dependents:
            raise ValueError("Paquet requis par " + dependents[0])
    _write_json(package.path / "state.json", {"enabled": enabled})
    legacy = package.path / "enabled"
    if legacy.exists():
        legacy.unlink()


def remove_package(
    package_id: str,
    plugins_dir: Path,
    force: bool = False,
) -> bool:
    """Supprime un paquet externe installé."""

    package_id = normalise_package_id(package_id)
    destination = plugins_dir / package_id
    if not destination.is_dir():
        return False
    if not force:
        dependents = _enabled_dependents(package_id, plugins_dir)
        if dependents:
            raise ValueError("Paquet requis par " + dependents[0])
    shutil.rmtree(destination)
    return True


def package_details(package_id: str, plugins_dir: Path) -> dict[str, Any]:
    """Construit une vue détaillée d’un paquet."""

    package = _installed_package(plugins_dir, package_id)
    manifest = validate_manifest(_read_json(package.path / "manifest.json"))
    return {
        **manifest,
        "enabled": package.enabled,
        "checksum": package.checksum,
        "issues": list(package.issues),
        "configuration_values": read_configuration(package_id, plugins_dir, redact=True),
    }
