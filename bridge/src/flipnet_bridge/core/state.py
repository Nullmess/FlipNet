"""Gère l’état persistant."""

from __future__ import annotations

import json
import secrets
import threading
import time
from copy import deepcopy
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from .data_store import DataStore


# Retourne l’horodatage courant.
def _now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds")


# Construit l’état initial.
def _default_state() -> dict[str, Any]:
    return {
        "monitors": [],
        "alerts": [],
        "next_monitor_id": 1,
        "pending_actions": [],
        "action_log": [],
        "web_results": [],
        "web_searches": [],
        "inventories": {},
        "inventory_changes": [],
        "timeline": [],
    }


class StateStore:
    """Gère l’état persistant de la passerelle."""

    # Initialise l’état persistant de la passerelle.
    def __init__(self, path: Path, data_path: Path | None = None) -> None:
        self.path = path
        self.data = DataStore(data_path or path.with_name("data.json"), legacy_state_path=path)
        self._lock = threading.RLock()
        self._data = _default_state()
        self._load()

    # Charge le fichier persistant.
    def _load(self) -> None:
        with self._lock:
            if not self.path.exists():
                self._save_locked()
                return
            try:
                raw = json.loads(self.path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                raw = {}
            merged = _default_state()
            for key in merged:
                if key in raw:
                    merged[key] = raw[key]
            self._data = merged

    # Enregistre les données sous verrou.
    def _save_locked(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(self.path.suffix + ".tmp")
        temporary.write_text(
            json.dumps(self._data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        temporary.replace(self.path)

    # Retourne une copie cohérente des données.
    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            return deepcopy(self._data)

    # Compte les éléments enregistrés.
    def counts(self) -> dict[str, int]:
        with self._lock:
            monitors = self._data["monitors"]
            data_counts = self.data.counts()
            return {
                "favorites": data_counts["favorites"],
                "history": data_counts["history"],
                "targets": data_counts["targets"],
                "groups": data_counts["groups"],
                "monitors": len(monitors),
                "monitor_ok": sum(1 for item in monitors if item.get("last_status") == "OK"),
                "monitor_error": sum(1 for item in monitors if item.get("last_status") == "ERR"),
                "alerts": sum(1 for item in self._data["alerts"] if not item.get("acknowledged")),
            }

    # Ajoute une entrée dans l’historique.
    def add_history(
        self,
        service: str,
        argument: str,
        status: str,
        title: str,
        body: str,
        limit: int = 24,
        target_id: str = "",
    ) -> None:
        if service in {
            "dashboard",
            "targets",
            "target_detail",
            "groups",
            "favorites",
            "favorite_last",
            "favorite_run",
            "history",
            "history_last",
            "history_clear",
            "monitors",
            "monitor_add",
            "monitor_remove",
            "monitor_run",
            "alerts",
            "alerts_clear",
            "playbook_list",
            "playbook_preview",
            "playbook_prepare",
            "action_prepare",
            "action_pending",
            "action_commit",
            "action_cancel",
            "action_log",
        }:
            return
        self.data.add_history(
            {
                "timestamp": _now(),
                "service": service,
                "argument": argument,
                "target_id": target_id,
                "status": status,
                "title": title,
                "body": body[:240],
            },
            limit,
        )

    # Liste l’historique enregistré.
    def history(self) -> list[dict[str, Any]]:
        return self.data.history()

    # Efface l’historique.
    def clear_history(self) -> None:
        self.data.clear_history()

    # Liste les favoris enregistrés.
    def favorites(self) -> list[dict[str, Any]]:
        return self.data.favorites()

    # Enregistre le dernier résultat du favori.
    def save_last_favorite(self) -> dict[str, Any]:
        try:
            last = self.data.last_history()
        except IndexError as exc:
            raise ValueError("Aucun resultat recent") from exc
        return self.data.save_favorite(
            {
                "label": last.get("title") or last.get("argument") or last.get("service"),
                "service": last.get("service", ""),
                "argument": last.get("argument", ""),
                "target_id": last.get("target_id", ""),
                "created_at": _now(),
            }
        )

    # Retourne un favori enregistré.
    def favorite(self, index: int) -> dict[str, Any]:
        try:
            return self.data.favorite(index)
        except IndexError as exc:
            raise IndexError("Favori introuvable") from exc

    # Retourne la dernière entrée de l’historique.
    def last_history(self) -> dict[str, Any]:
        try:
            return self.data.last_history()
        except IndexError as exc:
            raise IndexError("Aucun resultat recent") from exc

    # Liste les cibles enregistrées.
    def targets(self) -> list[dict[str, Any]]:
        return self.data.targets()

    # Retourne une cible enregistrée.
    def target(self, target_id: str) -> dict[str, Any]:
        return self.data.target(target_id)

    # Liste les groupes enregistrés.
    def groups(self) -> list[dict[str, Any]]:
        return self.data.groups()



    # Enregistre une recherche Web en cache.
    def cache_web_search(
        self, query: str, entries: list[dict[str, str]], limit: int = 64
    ) -> list[dict[str, Any]]:
        normalized_query = " ".join(query.split()).casefold()
        timestamp = _now()
        with self._lock:
            existing = {
                str(item.get("token", "")): dict(item)
                for item in self._data["web_results"]
                if str(item.get("token", ""))
            }
            tokens: list[str] = []
            for entry in entries:
                token = str(entry.get("token", ""))[:16].upper()
                url = str(entry.get("url", ""))[:2000]
                if not token or not url:
                    continue
                previous = existing.get(token, {})
                existing[token] = {
                    "token": token,
                    "url": url,
                    "title": str(entry.get("title", ""))[:160],
                    "source": str(entry.get("source", ""))[:120],
                    "snippet": str(entry.get("snippet", ""))[:500],
                    "page_title": str(previous.get("page_title", ""))[:160],
                    "page_body": str(previous.get("page_body", ""))[:6000],
                    "cached_at": timestamp,
                }
                tokens.append(token)
            ordered = [existing[token] for token in tokens if token in existing]
            ordered.extend(item for token, item in existing.items() if token not in tokens)
            self._data["web_results"] = ordered[:limit]
            searches = [
                item
                for item in self._data["web_searches"]
                if str(item.get("query", "")).casefold() != normalized_query
            ]
            searches.insert(
                0,
                {
                    "query": normalized_query,
                    "tokens": tokens[:8],
                    "cached_at": timestamp,
                },
            )
            self._data["web_searches"] = searches[:16]
            self._save_locked()
            return deepcopy([existing[token] for token in tokens if token in existing])

    # Charge une recherche Web en cache.
    def cached_web_search(self, query: str) -> list[dict[str, Any]]:
        normalized_query = " ".join(query.split()).casefold()
        with self._lock:
            search = next(
                (
                    item
                    for item in self._data["web_searches"]
                    if str(item.get("query", "")).casefold() == normalized_query
                ),
                None,
            )
            if search is None:
                return []
            by_token = {str(item.get("token", "")): item for item in self._data["web_results"]}
            return deepcopy(
                [by_token[token] for token in search.get("tokens", []) if token in by_token]
            )

    # Retourne un résultat Web en cache.
    def web_result(self, token: str) -> dict[str, Any]:
        normalized = token.strip().upper()
        with self._lock:
            for item in self._data["web_results"]:
                if str(item.get("token", "")).upper() == normalized:
                    return deepcopy(item)
        raise KeyError(token)

    # Enregistre le texte d’une page Web.
    def cache_web_page(self, token: str, title: str, body: str) -> None:
        normalized = token.strip().upper()
        with self._lock:
            for item in self._data["web_results"]:
                if str(item.get("token", "")).upper() != normalized:
                    continue
                item["page_title"] = title[:160]
                item["page_body"] = body[:6000]
                item["cached_at"] = _now()
                self._save_locked()
                return
        raise KeyError(token)

    def save_inventory(self, network: str, assets: list[dict[str, Any]]) -> dict[str, Any]:
        """Enregistre un instantané réseau."""
        key = str(network)[:64]
        timestamp = _now()
        normalized = []
        for raw in assets[:254]:
            normalized.append(
                {
                    "address": str(raw.get("address", ""))[:64],
                    "hostname": str(raw.get("hostname", ""))[:120],
                    "role": str(raw.get("role", "device"))[:40],
                    "services": [int(value) for value in raw.get("services", [])[:16]],
                    "seen_at": timestamp,
                }
            )
        with self._lock:
            previous = self._data["inventories"].get(key, {})
            old_assets = {item.get("address", ""): item for item in previous.get("assets", [])}
            new_assets = {item["address"]: item for item in normalized}
            added = sorted(set(new_assets) - set(old_assets))
            removed = sorted(set(old_assets) - set(new_assets))
            changed = sorted(
                address
                for address in set(new_assets) & set(old_assets)
                if new_assets[address].get("services") != old_assets[address].get("services")
                or new_assets[address].get("hostname") != old_assets[address].get("hostname")
            )
            change = {
                "network": key,
                "scanned_at": timestamp,
                "added": added,
                "removed": removed,
                "changed": changed,
                "total": len(normalized),
            }
            self._data["inventories"][key] = {
                "network": key,
                "scanned_at": timestamp,
                "assets": normalized,
            }
            if added or removed or changed:
                self._data["inventory_changes"].insert(0, change)
                self._data["timeline"].insert(
                    0,
                    {
                        "timestamp": timestamp,
                        "kind": "inventory",
                        "title": "Network changed",
                        "body": f"+{len(added)} -{len(removed)} ~{len(changed)}",
                        "severity": "WARN" if removed else "INFO",
                    },
                )
                del self._data["timeline"][64:]
                del self._data["inventory_changes"][32:]
            self._save_locked()
            return deepcopy(change)

    # Retourne le dernier inventaire.
    def inventory(self, network: str = "") -> dict[str, Any]:
        with self._lock:
            if network:
                item = self._data["inventories"].get(network)
                if item is None:
                    raise KeyError(network)
                return deepcopy(item)
            if not self._data["inventories"]:
                raise KeyError("inventory")
            item = max(
                self._data["inventories"].values(), key=lambda value: value.get("scanned_at", "")
            )
            return deepcopy(item)

    # Liste les changements réseau.
    def inventory_changes(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["inventory_changes"])

    # Ajoute un événement à la chronologie.
    def add_timeline(
        self, kind: str, title: str, body: str = "", severity: str = "INFO", limit: int = 64
    ) -> dict[str, Any]:
        entry = {
            "timestamp": _now(),
            "kind": kind[:24],
            "title": title[:64],
            "body": body[:160],
            "severity": severity[:12],
        }
        with self._lock:
            self._data["timeline"].insert(0, entry)
            del self._data["timeline"][limit:]
            self._save_locked()
        return deepcopy(entry)

    # Liste les événements récents.
    def timeline(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["timeline"])

    # Liste les surveillances enregistrées.
    def monitors(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["monitors"])

    # Retourne une surveillance enregistrée.
    def monitor(self, monitor_id: int) -> dict[str, Any]:
        with self._lock:
            for monitor in self._data["monitors"]:
                if int(monitor.get("id", -1)) == monitor_id:
                    return deepcopy(monitor)
        raise KeyError(monitor_id)

    # Ajoute une surveillance.
    def add_monitor(
        self, name: str, service: str, argument: str, interval_seconds: int
    ) -> dict[str, Any]:
        with self._lock:
            monitor = {
                "id": int(self._data["next_monitor_id"]),
                "name": name[:48],
                "service": service,
                "argument": argument,
                "interval_seconds": max(30, min(int(interval_seconds), 86400)),
                "last_status": "NEW",
                "last_title": "",
                "last_body": "",
                "last_checked": "",
                "next_check_epoch": 0.0,
            }
            self._data["next_monitor_id"] = monitor["id"] + 1
            self._data["monitors"].append(monitor)
            self._save_locked()
            return deepcopy(monitor)

    # Met à jour une surveillance.
    def update_monitor_definition(
        self,
        monitor_id: int,
        *,
        name: str,
        service: str,
        argument: str,
        interval_seconds: int,
    ) -> dict[str, Any]:
        with self._lock:
            for monitor in self._data["monitors"]:
                if int(monitor.get("id", -1)) != monitor_id:
                    continue
                monitor["name"] = name[:48]
                monitor["service"] = service[:64]
                monitor["argument"] = argument[:256]
                monitor["interval_seconds"] = max(30, min(int(interval_seconds), 86400))
                monitor["next_check_epoch"] = 0.0
                self._save_locked()
                return deepcopy(monitor)
        raise KeyError(monitor_id)

    # Retire une surveillance.
    def remove_monitor(self, monitor_id: int) -> bool:
        with self._lock:
            before = len(self._data["monitors"])
            self._data["monitors"] = [
                item for item in self._data["monitors"] if int(item.get("id", -1)) != monitor_id
            ]
            changed = len(self._data["monitors"]) != before
            if changed:
                self._save_locked()
            return changed

    # Liste les surveillances à exécuter.
    def due_monitors(self, now_epoch: float, force: bool = False) -> list[dict[str, Any]]:
        with self._lock:
            items = []
            for monitor in self._data["monitors"]:
                if force or float(monitor.get("next_check_epoch", 0.0)) <= now_epoch:
                    items.append(deepcopy(monitor))
            return items

    # Enregistre le résultat d’une surveillance.
    def update_monitor(
        self,
        monitor_id: int,
        status: str,
        title: str,
        body: str,
        now_epoch: float,
    ) -> tuple[str, dict[str, Any] | None]:
        with self._lock:
            for monitor in self._data["monitors"]:
                if int(monitor.get("id", -1)) != monitor_id:
                    continue
                previous = str(monitor.get("last_status", "NEW"))
                monitor["last_status"] = status
                monitor["last_title"] = title[:64]
                monitor["last_body"] = body[:240]
                monitor["last_checked"] = _now()
                monitor["next_check_epoch"] = now_epoch + int(monitor.get("interval_seconds", 60))
                self._save_locked()
                return previous, deepcopy(monitor)
        return "NEW", None

    # Retire les confirmations expirées sous verrou.
    def _prune_actions_locked(self, now_epoch: float | None = None) -> None:
        now = time.time() if now_epoch is None else now_epoch
        self._data["pending_actions"] = [
            item
            for item in self._data["pending_actions"]
            if float(item.get("expires_epoch", 0.0)) > now
        ]

    # Prépare une action protégée.
    def prepare_action(
        self,
        kind: str,
        operation: str,
        target: str,
        ttl_seconds: int,
        details: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        action = {
            "token": secrets.token_hex(3).upper(),
            "kind": kind,
            "operation": operation,
            "target": target,
            "created_at": _now(),
            "expires_epoch": time.time() + max(10, min(int(ttl_seconds), 120)),
        }
        if details:
            action.update(deepcopy(details))
        with self._lock:
            self._prune_actions_locked()
            self._data["pending_actions"] = [action]
            self._save_locked()
            return deepcopy(action)

    # Retourne l’action en attente.
    def pending_action(self) -> dict[str, Any]:
        with self._lock:
            self._prune_actions_locked()
            if not self._data["pending_actions"]:
                self._save_locked()
                raise IndexError("Aucune action en attente")
            return deepcopy(self._data["pending_actions"][-1])

    # Consomme une confirmation valide.
    def consume_action(self, token: str = "last") -> dict[str, Any]:
        with self._lock:
            self._prune_actions_locked()
            if not self._data["pending_actions"]:
                self._save_locked()
                raise IndexError("Aucune action en attente")
            if token == "last":
                index = len(self._data["pending_actions"]) - 1
            else:
                index = next(
                    (
                        i
                        for i, item in enumerate(self._data["pending_actions"])
                        if str(item.get("token", "")).upper() == token.upper()
                    ),
                    -1,
                )
                if index < 0:
                    raise IndexError("Action introuvable")
            action = self._data["pending_actions"].pop(index)
            self._save_locked()
            return deepcopy(action)

    # Annule une action protégée.
    def cancel_action(self, token: str = "last") -> dict[str, Any]:
        action = self.consume_action(token)
        self.record_action(action, "CANCELLED", "Annulee depuis le Flipper")
        return action

    # Ajoute une action au journal.
    def record_action(
        self,
        action: dict[str, Any],
        status: str,
        detail: str,
        limit: int = 24,
    ) -> None:
        entry = {
            "timestamp": _now(),
            "kind": str(action.get("kind", "")),
            "operation": str(action.get("operation", "")),
            "target": str(action.get("target", "")),
            "status": status,
            "detail": detail[:240],
        }
        with self._lock:
            self._data["action_log"].insert(0, entry)
            del self._data["action_log"][limit:]
            self._save_locked()

    # Liste le journal des actions.
    def action_log(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["action_log"])

    # Ajoute une alerte.
    def add_alert(self, title: str, body: str, limit: int = 24) -> dict[str, Any]:
        alert = {
            "timestamp": _now(),
            "title": title[:64],
            "body": body[:240],
            "acknowledged": False,
        }
        with self._lock:
            self._data["alerts"].insert(0, alert)
            del self._data["alerts"][limit:]
            self._data["timeline"].insert(
                0,
                {
                    "timestamp": alert["timestamp"],
                    "kind": "alert",
                    "title": alert["title"],
                    "body": alert["body"],
                    "severity": "HIGH",
                },
            )
            del self._data["timeline"][64:]
            self._save_locked()
        return deepcopy(alert)

    # Liste les alertes enregistrées.
    def alerts(self) -> list[dict[str, Any]]:
        with self._lock:
            return deepcopy(self._data["alerts"])

    # Efface les alertes.
    def clear_alerts(self) -> None:
        with self._lock:
            for alert in self._data["alerts"]:
                alert["acknowledged"] = True
            self._save_locked()
