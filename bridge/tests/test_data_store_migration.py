from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from flipnet_bridge.core.data_store import (
    SCHEMA_VERSION,
    DataStore,
    DataValidationError,
    migrate_payload,
)


class DataStoreMigrationTests(unittest.TestCase):
    def test_historical_schema_3_is_migrated_and_backed_up(self) -> None:
        payload = {
            "schema_version": 3,
            "metadata": {
                "created_at": "2026-01-01T00:00:00+00:00",
                "updated_at": "2026-01-02T00:00:00+00:00",
            },
            "history": [
                {
                    "timestamp": "2026-01-02T00:00:00+00:00",
                    "service": "dns",
                    "argument": "example.com",
                    "status": "OK",
                    "title": "DNS",
                    "body": "192.0.2.10",
                }
            ],
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "data.json"
            path.write_text(json.dumps(payload), encoding="utf-8")

            store = DataStore(path)

            current = store.snapshot()
            self.assertEqual(current["schema_version"], SCHEMA_VERSION)
            self.assertEqual(current["history"][0]["service"], "dns")
            self.assertEqual(current["targets"], [])
            self.assertEqual(current["groups"], [])
            self.assertEqual(current["favorites"], [])
            self.assertTrue(path.with_suffix(".json.bak").is_file())
            backup = json.loads(path.with_suffix(".json.bak").read_text(encoding="utf-8"))
            self.assertEqual(backup["schema_version"], 3)

            store.import_from(path)
            backup_after_manual_migrate = json.loads(
                path.with_suffix(".json.bak").read_text(encoding="utf-8")
            )
            self.assertEqual(backup_after_manual_migrate["schema_version"], 3)

    def test_schema_2_catalogue_keeps_workspace_collections(self) -> None:
        payload = {
            "schema_version": 2,
            "metadata": {
                "created_at": "2026-02-01T00:00:00+00:00",
                "updated_at": "2026-02-02T00:00:00+00:00",
            },
            "groups": [
                {
                    "id": "lab",
                    "name": "Lab",
                    "description": "",
                    "tags": [],
                    "created_at": "2026-02-01T00:00:00+00:00",
                    "updated_at": "2026-02-01T00:00:00+00:00",
                }
            ],
            "targets": [
                {
                    "id": "router",
                    "name": "Router",
                    "host": "192.0.2.1",
                    "port": None,
                    "protocol": "auto",
                    "group_id": "lab",
                    "credential_ref": "",
                    "tags": [],
                    "enabled": True,
                    "created_at": "2026-02-01T00:00:00+00:00",
                    "updated_at": "2026-02-01T00:00:00+00:00",
                }
            ],
            "favorites": [
                {
                    "label": "DNS",
                    "service": "dns",
                    "argument": "example.com",
                    "target_id": "router",
                    "created_at": "2026-02-01T00:00:00+00:00",
                }
            ],
            "history": [
                {
                    "timestamp": "2026-02-02T00:00:00+00:00",
                    "service": "dns",
                    "argument": "example.com",
                    "target_id": "router",
                    "status": "OK",
                    "title": "DNS",
                    "body": "OK",
                }
            ],
        }

        migrated = migrate_payload(payload)

        self.assertEqual(migrated["schema_version"], SCHEMA_VERSION)
        self.assertEqual(migrated["groups"][0]["id"], "lab")
        self.assertEqual(migrated["targets"][0]["id"], "router")
        self.assertEqual(migrated["favorites"][0]["target_id"], "router")
        self.assertEqual(migrated["history"][0]["target_id"], "router")

    def test_future_schema_is_rejected(self) -> None:
        with self.assertRaises(DataValidationError):
            migrate_payload({"schema_version": SCHEMA_VERSION + 1})


if __name__ == "__main__":
    unittest.main()
