"""Gère le catalogue persistant."""

from __future__ import annotations

import json
import os
import re
import shutil
import threading
from copy import deepcopy
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 4
MIGRATABLE_SCHEMA_VERSIONS = frozenset((0, 1, 2, 3, SCHEMA_VERSION, None))
ID_PATTERN = re.compile(r"^[a-z][a-z0-9._-]{0,63}$")
CREDENTIAL_PATTERN = re.compile(r"^[a-z][a-z0-9._:/-]{0,127}$")


class DataValidationError(ValueError):
    """Signale un catalogue de données invalide."""


# Retourne l’horodatage courant.
def _now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds")


# Construit le catalogue initial.
def _default_data() -> dict[str, Any]:
    timestamp = _now()
    return {
        "schema_version": SCHEMA_VERSION,
        "metadata": {"created_at": timestamp, "updated_at": timestamp},
        "targets": [],
        "groups": [],
        "favorites": [],
        "history": [],
    }


# Valide une liste obligatoire.
def _require_list(payload: dict[str, Any], key: str) -> list[Any]:
    value = payload.get(key)
    if not isinstance(value, list):
        raise DataValidationError(f"{key} must be a list")
    return value


# Nettoie une valeur textuelle.
def _text(value: Any, field: str, maximum: int, *, required: bool = True) -> str:
    if value is None and not required:
        return ""
    if not isinstance(value, str):
        raise DataValidationError(f"{field} must be a string")
    result = value.strip()
    if required and not result:
        raise DataValidationError(f"{field} is required")
    if len(result) > maximum:
        raise DataValidationError(f"{field} exceeds {maximum} characters")
    return result


# Valide puis normalise un identifiant.
def _identifier(value: Any, field: str) -> str:
    result = _text(value, field, 64)
    if not ID_PATTERN.fullmatch(result):
        raise DataValidationError(f"{field} has an invalid identifier")
    return result


# Nettoie une liste de chaînes.
def _string_list(value: Any, field: str, maximum_items: int = 16) -> list[str]:
    if not isinstance(value, list):
        raise DataValidationError(f"{field} must be a list")
    if len(value) > maximum_items:
        raise DataValidationError(f"{field} has too many entries")
    return [_text(item, field, 64) for item in value]


# Normalise le groupe.
def _normalise_group(item: Any) -> dict[str, Any]:
    if not isinstance(item, dict):
        raise DataValidationError("group must be an object")
    return {
        "id": _identifier(item.get("id"), "group.id"),
        "name": _text(item.get("name"), "group.name", 64),
        "description": _text(item.get("description", ""), "group.description", 160, required=False),
        "tags": _string_list(item.get("tags", []), "group.tags"),
        "created_at": _text(item.get("created_at", _now()), "group.created_at", 40),
        "updated_at": _text(item.get("updated_at", _now()), "group.updated_at", 40),
    }


# Normalise la cible.
def _normalise_target(item: Any) -> dict[str, Any]:
    if not isinstance(item, dict):
        raise DataValidationError("target must be an object")
    port = item.get("port")
    if port in (None, ""):
        port_value = None
    elif isinstance(port, bool):
        raise DataValidationError("target.port must be an integer")
    else:
        try:
            port_value = int(port)
        except (TypeError, ValueError) as exc:
            raise DataValidationError("target.port must be an integer") from exc
        if not 1 <= port_value <= 65535:
            raise DataValidationError("target.port must be between 1 and 65535")
    credential_ref = _text(
        item.get("credential_ref", ""), "target.credential_ref", 128, required=False
    )
    if credential_ref and not CREDENTIAL_PATTERN.fullmatch(credential_ref):
        raise DataValidationError("target.credential_ref has an invalid reference")
    return {
        "id": _identifier(item.get("id"), "target.id"),
        "name": _text(item.get("name"), "target.name", 64),
        "host": _text(item.get("host"), "target.host", 253),
        "port": port_value,
        "protocol": _text(item.get("protocol", "auto"), "target.protocol", 24).lower(),
        "group_id": _text(item.get("group_id", ""), "target.group_id", 64, required=False),
        "credential_ref": credential_ref,
        "tags": _string_list(item.get("tags", []), "target.tags"),
        "enabled": bool(item.get("enabled", True)),
        "created_at": _text(item.get("created_at", _now()), "target.created_at", 40),
        "updated_at": _text(item.get("updated_at", _now()), "target.updated_at", 40),
    }


# Normalise le favori.
def _normalise_favorite(item: Any) -> dict[str, Any]:
    if not isinstance(item, dict):
        raise DataValidationError("favorite must be an object")
    return {
        "label": _text(item.get("label", "Favorite"), "favorite.label", 64),
        "service": _text(item.get("service"), "favorite.service", 64),
        "argument": _text(item.get("argument", ""), "favorite.argument", 256, required=False),
        "target_id": _text(item.get("target_id", ""), "favorite.target_id", 64, required=False),
        "created_at": _text(item.get("created_at", _now()), "favorite.created_at", 40),
    }


# Normalise l’historique.
def _normalise_history(item: Any) -> dict[str, Any]:
    if not isinstance(item, dict):
        raise DataValidationError("history entry must be an object")
    return {
        "timestamp": _text(item.get("timestamp", _now()), "history.timestamp", 40),
        "service": _text(item.get("service"), "history.service", 64),
        "argument": _text(item.get("argument", ""), "history.argument", 256, required=False),
        "target_id": _text(item.get("target_id", ""), "history.target_id", 64, required=False),
        "status": _text(item.get("status", "OK"), "history.status", 16),
        "title": _text(item.get("title", "Result"), "history.title", 96),
        "body": _text(item.get("body", ""), "history.body", 240, required=False),
    }


def validate_payload(payload: Any) -> dict[str, Any]:
    """Valide le catalogue persistant."""

    if not isinstance(payload, dict):
        raise DataValidationError("root must be an object")
    version = payload.get("schema_version")
    if version != SCHEMA_VERSION:
        raise DataValidationError(f"unsupported schema_version: {version}")
    metadata = payload.get("metadata", {})
    if not isinstance(metadata, dict):
        raise DataValidationError("metadata must be an object")
    result = {
        "schema_version": SCHEMA_VERSION,
        "metadata": {
            "created_at": _text(metadata.get("created_at", _now()), "metadata.created_at", 40),
            "updated_at": _text(metadata.get("updated_at", _now()), "metadata.updated_at", 40),
        },
        "targets": [_normalise_target(item) for item in _require_list(payload, "targets")],
        "groups": [_normalise_group(item) for item in _require_list(payload, "groups")],
        "favorites": [_normalise_favorite(item) for item in _require_list(payload, "favorites")],
        "history": [_normalise_history(item) for item in _require_list(payload, "history")],
    }
    for collection in ("targets", "groups"):
        identifiers = [item["id"] for item in result[collection]]
        if len(identifiers) != len(set(identifiers)):
            raise DataValidationError(f"duplicate identifier in {collection}")
    group_ids = {item["id"] for item in result["groups"]}
    target_ids = {item["id"] for item in result["targets"]}
    for target in result["targets"]:
        if target["group_id"] and target["group_id"] not in group_ids:
            raise DataValidationError(f"unknown group_id: {target['group_id']}")
    for item in result["favorites"] + result["history"]:
        if item.get("target_id") and item["target_id"] not in target_ids:
            raise DataValidationError(f"unknown target_id: {item['target_id']}")
    return result


def migrate_payload(payload: Any) -> dict[str, Any]:
    """Met à niveau un ancien catalogue."""

    if not isinstance(payload, dict):
        raise DataValidationError("root must be an object")
    version = payload.get("schema_version", 0)
    if version == SCHEMA_VERSION:
        return validate_payload(deepcopy(payload))
    if isinstance(version, bool) or version not in MIGRATABLE_SCHEMA_VERSIONS:
        raise DataValidationError(f"unsupported schema_version: {version}")

    migrated = _default_data()

    metadata = payload.get("metadata")
    if isinstance(metadata, dict):
        migrated["metadata"] = {
            "created_at": metadata.get("created_at", migrated["metadata"]["created_at"]),
            "updated_at": metadata.get("updated_at", migrated["metadata"]["updated_at"]),
        }

    for key in ("targets", "groups", "favorites", "history"):
        value = payload.get(key)
        if isinstance(value, list):
            migrated[key] = deepcopy(value)

    # Les tout premiers catalogues pouvaient stocker un ancien profile_id.
    # Le schema 3 historique ne contenait quant à lui que metadata + history.
    # Dans les deux cas les champs absents sont reconstruits par _default_data().
    for target in migrated["targets"]:
        if isinstance(target, dict):
            target.pop("profile_id", None)
    return validate_payload(migrated)


class DataStore:
    """Gère le catalogue persistant de FlipNet."""

    # Initialise le catalogue persistant de FlipNet.
    def __init__(self, path: Path, legacy_state_path: Path | None = None) -> None:
        self.path = path
        self.backup_path = path.with_suffix(path.suffix + ".bak")
        self._lock = threading.RLock()
        self._data = _default_data()
        self._load(legacy_state_path)

    # Charge le fichier persistant.
    def _load(self, legacy_state_path: Path | None) -> None:
        with self._lock:
            if self.path.exists():
                try:
                    raw = json.loads(self.path.read_text(encoding="utf-8"))
                    migrated = migrate_payload(raw)
                except (OSError, json.JSONDecodeError, DataValidationError) as exc:
                    raise DataValidationError(f"invalid data file {self.path}: {exc}") from exc
                self._data = migrated
                if raw != migrated:
                    self._save_locked(create_backup=True)
                return
            data = _default_data()
            if legacy_state_path is not None and legacy_state_path.exists():
                try:
                    legacy = json.loads(legacy_state_path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError):
                    legacy = {}
                if isinstance(legacy, dict):
                    for key in ("favorites", "history"):
                        if isinstance(legacy.get(key), list):
                            data[key] = legacy[key]
            self._data = validate_payload(data)
            self._save_locked(create_backup=False)

    # Enregistre les données sous verrou.
    def _save_locked(self, *, create_backup: bool = True) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._data["metadata"]["updated_at"] = _now()
        temporary = self.path.with_suffix(self.path.suffix + ".tmp")
        with temporary.open("w", encoding="utf-8", newline="\n") as handle:
            json.dump(self._data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        if create_backup and self.path.exists():
            shutil.copy2(self.path, self.backup_path)
        temporary.replace(self.path)

    # Retourne une copie cohérente des données.
    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return deepcopy(self._data)

    # Compte les éléments enregistrés.
    def counts(self) -> dict[str, int]:
        with self._lock:
            return {
                "targets": len(self._data["targets"]),
                "groups": len(self._data["groups"]),
                "favorites": len(self._data["favorites"]),
                "history": len(self._data["history"]),
            }

    # Exporte le catalogue vers un fichier.
    def export_to(self, destination: Path) -> Path:
        with self._lock:
            destination = destination.expanduser().resolve()
            destination.parent.mkdir(parents=True, exist_ok=True)
            temporary = destination.with_suffix(destination.suffix + ".tmp")
            temporary.write_text(
                json.dumps(self._data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            temporary.replace(destination)
            return destination

    # Importe un catalogue depuis un fichier.
    def import_from(self, source: Path) -> None:
        source = source.expanduser().resolve()
        try:
            raw = json.loads(source.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise DataValidationError(f"cannot read import file: {exc}") from exc
        migrated = migrate_payload(raw)
        with self._lock:
            # `data migrate` charge déjà le fichier via __init__. Si cette
            # lecture a provoqué une migration, _load() a créé le .bak avec
            # l'original. Ne réécrivons pas aussitôt ce backup avec le fichier
            # déjà migré.
            if source == self.path.expanduser().resolve() and migrated == self._data:
                return
            self._data = migrated
            self._save_locked(create_backup=True)

    # Liste les groupes enregistrés.
    def groups(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["groups"])



    # Liste les cibles enregistrées.
    def targets(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["targets"])

    # Retourne une cible enregistrée.
    def target(self, target_id: str) -> dict[str, Any]:
        with self._lock:
            for item in self._data["targets"]:
                if item["id"] == target_id:
                    return deepcopy(item)
        raise KeyError(target_id)

    # Construit un identifiant unique sous verrou.
    def _unique_id_locked(self, requested: str, collection: str) -> str:
        base = re.sub(r"[^a-z0-9._-]+", "-", requested.lower()).strip("-._") or "item"
        if not base[0].isalpha():
            base = "item-" + base
        base = base[:56]
        existing = {item["id"] for item in self._data[collection]}
        candidate = base
        suffix = 2
        while candidate in existing:
            candidate = f"{base}-{suffix}"
            suffix += 1
        return candidate

    # Ajoute un groupe.
    def add_group(
        self, name: str, description: str = "", tags: list[str] | None = None
    ) -> dict[str, Any]:
        timestamp = _now()
        with self._lock:
            item = _normalise_group(
                {
                    "id": self._unique_id_locked(name, "groups"),
                    "name": name,
                    "description": description,
                    "tags": tags or [],
                    "created_at": timestamp,
                    "updated_at": timestamp,
                }
            )
            self._data["groups"].append(item)
            self._save_locked()
            return deepcopy(item)

    # Retourne un groupe enregistré.
    def group(self, group_id: str) -> dict[str, Any]:
        with self._lock:
            for item in self._data["groups"]:
                if item["id"] == group_id:
                    return deepcopy(item)
        raise KeyError(group_id)

    # Met à jour un groupe.
    def update_group(
        self,
        group_id: str,
        *,
        name: str,
        description: str = "",
        tags: list[str] | None = None,
    ) -> dict[str, Any]:
        with self._lock:
            for index, current in enumerate(self._data["groups"]):
                if current["id"] != group_id:
                    continue
                item = _normalise_group(
                    {
                        **current,
                        "name": name,
                        "description": description,
                        "tags": tags or [],
                        "updated_at": _now(),
                    }
                )
                self._data["groups"][index] = item
                self._save_locked()
                return deepcopy(item)
        raise KeyError(group_id)

    # Retire un groupe.
    def remove_group(self, group_id: str) -> bool:
        with self._lock:
            if any(item.get("group_id") == group_id for item in self._data["targets"]):
                raise DataValidationError("group is still referenced by a target")
            before = len(self._data["groups"])
            self._data["groups"] = [item for item in self._data["groups"] if item["id"] != group_id]
            if len(self._data["groups"]) == before:
                return False
            self._save_locked()
            return True

    # Ajoute une cible.
    def add_target(
        self,
        name: str,
        host: str,
        *,
        port: int | None = None,
        protocol: str = "auto",
        group_id: str = "",
        credential_ref: str = "",
        tags: list[str] | None = None,
    ) -> dict[str, Any]:
        timestamp = _now()
        with self._lock:
            item = _normalise_target(
                {
                    "id": self._unique_id_locked(name, "targets"),
                    "name": name,
                    "host": host,
                    "port": port,
                    "protocol": protocol,
                    "group_id": group_id,
                    "credential_ref": credential_ref,
                    "tags": tags or [],
                    "enabled": True,
                    "created_at": timestamp,
                    "updated_at": timestamp,
                }
            )
            group_ids = {group["id"] for group in self._data["groups"]}
            if item["group_id"] and item["group_id"] not in group_ids:
                raise DataValidationError(f"unknown group_id: {item['group_id']}")
            self._data["targets"].append(item)
            self._save_locked()
            return deepcopy(item)

    # Met à jour une cible.
    def update_target(
        self,
        target_id: str,
        *,
        name: str,
        host: str,
        port: int | None = None,
        protocol: str = "auto",
        group_id: str = "",
        credential_ref: str = "",
        tags: list[str] | None = None,
        enabled: bool = True,
    ) -> dict[str, Any]:
        with self._lock:
            group_ids = {group["id"] for group in self._data["groups"]}
            for index, current in enumerate(self._data["targets"]):
                if current["id"] != target_id:
                    continue
                item = _normalise_target(
                    {
                        **current,
                        "name": name,
                        "host": host,
                        "port": port,
                        "protocol": protocol,
                        "group_id": group_id,
                        "credential_ref": credential_ref,
                        "tags": tags or [],
                        "enabled": enabled,
                        "updated_at": _now(),
                    }
                )
                if item["group_id"] and item["group_id"] not in group_ids:
                    raise DataValidationError(f"unknown group_id: {item['group_id']}")
                self._data["targets"][index] = item
                self._save_locked()
                return deepcopy(item)
        raise KeyError(target_id)

    # Retire une cible.
    def remove_target(self, target_id: str) -> bool:
        with self._lock:
            before = len(self._data["targets"])
            self._data["targets"] = [
                item for item in self._data["targets"] if item["id"] != target_id
            ]
            if len(self._data["targets"]) == before:
                return False
            for collection in ("favorites", "history"):
                for item in self._data[collection]:
                    if item.get("target_id") == target_id:
                        item["target_id"] = ""
            self._save_locked()
            return True

    # Liste les favoris enregistrés.
    def favorites(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["favorites"])

    # Enregistre un favori.
    def save_favorite(self, favorite: dict[str, Any]) -> dict[str, Any]:
        item = _normalise_favorite(favorite)
        with self._lock:
            duplicate = next(
                (
                    current
                    for current in self._data["favorites"]
                    if current["service"] == item["service"]
                    and current["argument"] == item["argument"]
                    and current["target_id"] == item["target_id"]
                ),
                None,
            )
            if duplicate is not None:
                return deepcopy(duplicate)
            self._data["favorites"].insert(0, item)
            del self._data["favorites"][16:]
            self._save_locked()
            return deepcopy(item)

    # Retire un favori.
    def remove_favorite(self, index: int) -> bool:
        with self._lock:
            if index < 1 or index > len(self._data["favorites"]):
                return False
            self._data["favorites"].pop(index - 1)
            self._save_locked()
            return True

    # Retourne un favori enregistré.
    def favorite(self, index: int) -> dict[str, Any]:
        with self._lock:
            if index < 1 or index > len(self._data["favorites"]):
                raise IndexError("Favorite not found")
            return deepcopy(self._data["favorites"][index - 1])

    # Liste l’historique enregistré.
    def history(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["history"])

    # Ajoute une entrée dans l’historique.
    def add_history(self, item: dict[str, Any], limit: int = 24) -> None:
        entry = _normalise_history(item)
        with self._lock:
            self._data["history"].insert(0, entry)
            del self._data["history"][max(1, min(limit, 256)) :]
            self._save_locked()

    # Retourne la dernière entrée de l’historique.
    def last_history(self) -> dict[str, Any]:
        with self._lock:
            if not self._data["history"]:
                raise IndexError("No recent result")
            return deepcopy(self._data["history"][0])

    # Efface l’historique.
    def clear_history(self) -> None:
        with self._lock:
            self._data["history"] = []
            self._save_locked()
