"""Gère la ligne de commande FlipNet."""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

from . import __version__
from .core.data_store import DataStore, DataValidationError, migrate_payload
from .core.platform_paths import (
    default_config_path,
    default_data_path,
    default_log_path,
    default_plugins_path,
    default_state_path,
)


# Construit les options de commande.
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Passerelle USB et BLE pour FlipNet")
    parser.add_argument("--version", action="version", version=f"FlipNet Bridge {__version__}")
    parser.add_argument(
        "--config", type=Path, default=default_config_path(), help="fichier JSON de configuration"
    )
    parser.add_argument(
        "--data-file", type=Path, default=default_data_path(), help="catalogue JSON versionne"
    )
    parser.add_argument(
        "--transport",
        choices=("auto", "usb", "ble"),
        default="usb",
        help="usb par defaut; auto lance USB et BLE simultanement",
    )
    parser.add_argument("--port", help="port USB FlipNet, par exemple /dev/ttyACM1 ou COM6")
    parser.add_argument("--device", help="adresse BLE precise du Flipper")
    parser.add_argument("--once", action="store_true", help="ne pas se reconnecter automatiquement")
    parser.add_argument("--debug", action="store_true", help="activer les journaux detailles")
    parser.add_argument(
        "--log-file",
        type=Path,
        help=f"ecrire les journaux dans un fichier (defaut conseille: {default_log_path()})",
    )
    parser.add_argument(
        "--service-mode",
        action="store_true",
        help="mode service: active automatiquement le fichier de journalisation par defaut",
    )

    commands = parser.add_subparsers(dest="command")
    data = commands.add_parser("data", help="valider, importer, exporter ou migrer data.json")
    data.add_argument("action", choices=("path", "show", "validate", "export", "import", "migrate"))
    data.add_argument("file", type=Path, nargs="?")

    target = commands.add_parser("target", help="gerer les cibles enregistrees")
    target.add_argument("action", choices=("list", "show", "add", "remove"))
    target.add_argument("identifier", nargs="?")
    target.add_argument("--name")
    target.add_argument("--host")
    target.add_argument("--port-number", type=int)
    target.add_argument("--protocol", default="auto")
    target.add_argument("--group", default="")
    target.add_argument("--credential-ref", default="")
    target.add_argument("--tag", action="append", default=[])

    group = commands.add_parser("group", help="gerer les groupes de cibles")
    group.add_argument("action", choices=("list", "add", "remove"))
    group.add_argument("identifier", nargs="?")
    group.add_argument("--name")
    group.add_argument("--description", default="")
    group.add_argument("--tag", action="append", default=[])


    config = commands.add_parser("config", help="valider ou migrer config.json")
    config.add_argument("action", choices=("path", "show", "validate", "status", "migrate"))
    config.add_argument("file", type=Path, nargs="?")
    config.add_argument("--json", action="store_true", help="sortie JSON")
    config.add_argument("--dry-run", action="store_true", help="ne pas modifier le fichier")

    compatibility = commands.add_parser(
        "compatibility", help="afficher les formats pris en charge"
    )
    compatibility.add_argument("--json", action="store_true", help="sortie JSON")

    doctor = commands.add_parser("doctor", help="generer un diagnostic de support local")
    doctor.add_argument("--json", action="store_true", help="sortie JSON")
    doctor.add_argument(
        "--strict", action="store_true", help="echouer si un prerequis critique manque"
    )

    plugin = commands.add_parser("plugin", help="gerer les plugins communautaires")
    plugin.add_argument(
        "action",
        choices=(
            "list",
            "show",
            "doctor",
            "validate",
            "install",
            "update",
            "remove",
            "enable",
            "disable",
            "config",
            "catalog",
            "updates",
            "simulate",
        ),
    )
    plugin.add_argument("value", nargs="?")
    plugin.add_argument("--sha256", default="")
    plugin.add_argument("--input", default="test")
    plugin.add_argument("--set", action="append", default=[])
    plugin.add_argument("--unset", action="append", default=[])
    plugin.add_argument("--force", action="store_true")
    return parser


# Configure les journaux.
def configure_logging(debug: bool, log_file: Path | None, service_mode: bool) -> Path | None:
    if service_mode and log_file is None:
        log_file = default_log_path()
    handlers: list[logging.Handler] = []
    if sys.stderr is not None:
        handlers.append(logging.StreamHandler())
    if log_file is not None:
        log_file = log_file.expanduser().resolve()
        log_file.parent.mkdir(parents=True, exist_ok=True)
        handlers.append(logging.FileHandler(log_file, encoding="utf-8"))
    if not handlers:
        handlers.append(logging.NullHandler())
    logging.basicConfig(
        level=logging.DEBUG if debug else logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        handlers=handlers,
        force=True,
    )
    return log_file


# Affiche une liste en JSON ou en texte.
def _print_items(items: list[dict[str, object]], empty: str) -> None:
    if not items:
        print(empty)
        return
    for item in items:
        suffix = f" ({item.get('host')})" if item.get("host") else ""
        print(f"{item.get('id')}: {item.get('name')}{suffix}")


# Exécute une commande du catalogue.
def run_catalog_command(args: argparse.Namespace) -> int:
    if args.command == "compatibility":
        from .core.compatibility import current_contract, render_contract

        print(render_contract(current_contract(), as_json=args.json))
        return 0
    if args.command == "config":
        import json
        from dataclasses import asdict

        from .core.config import (
            config_to_payload,
            load_config,
            migrate_config_file,
            validate_config_payload,
        )

        source = (args.file or args.config).expanduser().resolve()
        if args.action == "path":
            print(source)
            return 0
        if not source.exists():
            raise ValueError("configuration file not found")
        raw = json.loads(source.read_text(encoding="utf-8"))
        if args.action == "show":
            print(json.dumps(config_to_payload(load_config(source)), ensure_ascii=False, indent=2))
        elif args.action == "validate":
            _, report = validate_config_payload(raw)
            print(json.dumps(asdict(report), ensure_ascii=False, indent=2))
        elif args.action == "status":
            _, report = validate_config_payload(raw)
            if args.json:
                print(json.dumps(asdict(report), ensure_ascii=False, indent=2))
            else:
                state = "migration required" if report.changed else "current"
                print(f"Config schema {report.source_schema}: {state}")
        elif args.action == "migrate":
            report = migrate_config_file(source, dry_run=args.dry_run)
            if args.json:
                print(json.dumps(asdict(report), ensure_ascii=False, indent=2))
            elif args.dry_run:
                print("Migration preview only")
            elif report.changed:
                print(f"Migrated config: {source}")
                if report.backup_path:
                    print(f"Backup: {report.backup_path}")
            else:
                print("Configuration is current")
        return 0
    store = DataStore(args.data_file, legacy_state_path=default_state_path())
    if args.command == "data":
        if args.action == "path":
            print(store.path)
        elif args.action == "show":
            import json

            print(json.dumps(store.snapshot(), ensure_ascii=False, indent=2))
        elif args.action == "validate":
            source = args.file or store.path
            import json

            migrate_payload(json.loads(source.expanduser().read_text(encoding="utf-8")))
            print(f"Valid: {source}")
        elif args.action == "export":
            if args.file is None:
                raise DataValidationError("export requires a destination file")
            print(store.export_to(args.file))
        elif args.action == "import":
            if args.file is None:
                raise DataValidationError("import requires a source file")
            store.import_from(args.file)
            print(f"Imported: {args.file}")
        elif args.action == "migrate":
            store.import_from(store.path)
            print(f"Data catalog migrated: {store.path}")
        return 0
    if args.command == "target":
        if args.action == "list":
            _print_items(store.targets(), "No saved target")
        elif args.action == "show":
            if not args.identifier:
                raise DataValidationError("target show requires an identifier")
            import json

            print(json.dumps(store.target(args.identifier), ensure_ascii=False, indent=2))
        elif args.action == "add":
            if not args.name or not args.host:
                raise DataValidationError("target add requires --name and --host")
            item = store.add_target(
                args.name,
                args.host,
                port=args.port_number,
                protocol=args.protocol,
                group_id=args.group,
                credential_ref=args.credential_ref,
                tags=args.tag,
            )
            print(f"Added target: {item['id']}")
        elif args.action == "remove":
            if not args.identifier or not store.remove_target(args.identifier):
                raise DataValidationError("target not found")
            print(f"Removed target: {args.identifier}")
        return 0
    if args.command == "group":
        if args.action == "list":
            _print_items(store.groups(), "No saved group")
        elif args.action == "add":
            if not args.name:
                raise DataValidationError("group add requires --name")
            item = store.add_group(args.name, args.description, args.tag)
            print(f"Added group: {item['id']}")
        elif args.action == "remove":
            if not args.identifier or not store.remove_group(args.identifier):
                raise DataValidationError("group not found")
            print(f"Removed group: {args.identifier}")
        return 0
    if args.command == "doctor":
        from .core.diagnostics import collect_report, critical_failures, render_report

        report = collect_report()
        print(render_report(report, as_json=args.json))
        if args.strict and critical_failures(report):
            return 2
        return 0
    if args.command == "plugin":
        import json

        from .core.config import load_config
        from .plugins.catalog import available_updates, fetch_catalog
        from .plugins.package import (
            audit_packages,
            configure_package,
            install_package,
            list_packages,
            package_details,
            remove_package,
            set_enabled,
            update_package,
            validate_package,
        )
        from .plugins.runtime import load_plugins

        plugins_dir = default_plugins_path()
        if args.action == "list":
            packages = list_packages(plugins_dir)
            if not packages:
                print("No installed plugin package")
            for package in packages:
                status = "enabled" if package.enabled else "disabled"
                if package.issues:
                    status = "invalid"
                service_list = ", ".join(package.services)
                print(f"{package.package_id} {package.version} [{status}] - {service_list}")
                for issue in package.issues:
                    print(f"  ! {issue}")
        elif args.action == "show":
            if not args.value:
                raise ValueError("plugin show requires a package id")
            print(
                json.dumps(
                    package_details(args.value, plugins_dir),
                    ensure_ascii=False,
                    indent=2,
                )
            )
        elif args.action == "doctor":
            audits = audit_packages(plugins_dir)
            if not audits:
                print("No installed plugin package")
            for audit in audits:
                status = "ok" if audit.valid else "invalid"
                enabled = "enabled" if audit.enabled else "disabled"
                print(f"{audit.package_id} [{status}] [{enabled}]")
                for issue in audit.issues:
                    print(f"  ! {issue}")
        elif args.action == "validate":
            if not args.value:
                raise ValueError("plugin validate requires a package path")
            print(json.dumps(validate_package(Path(args.value)), ensure_ascii=False, indent=2))
        elif args.action == "install":
            if not args.value:
                raise ValueError("plugin install requires a package path")
            package = install_package(Path(args.value), plugins_dir, args.sha256)
            print(f"Installed plugin: {package.package_id} {package.version}")
        elif args.action == "update":
            if not args.value:
                raise ValueError("plugin update requires a package path")
            package = update_package(Path(args.value), plugins_dir, args.sha256)
            print(f"Updated plugin: {package.package_id} {package.version}")
        elif args.action == "remove":
            if not args.value or not remove_package(args.value, plugins_dir, args.force):
                raise ValueError("plugin package not found")
            print(f"Removed plugin: {args.value}")
        elif args.action in {"enable", "disable"}:
            if not args.value:
                raise ValueError("plugin id required")
            set_enabled(
                args.value,
                plugins_dir,
                args.action == "enable",
                args.force,
            )
            print(f"{args.action.title()}d plugin: {args.value}")
        elif args.action == "config":
            if not args.value:
                raise ValueError("plugin config requires a package id")
            assignments = {}
            for item in args.set:
                if "=" not in item:
                    raise ValueError("plugin config --set requires key=value")
                key, value = item.split("=", 1)
                assignments[key.strip()] = value
            values = configure_package(
                args.value,
                plugins_dir,
                assignments,
                args.unset,
            )
            print(json.dumps(values, ensure_ascii=False, indent=2))
        elif args.action == "catalog":
            config = load_config(args.config)
            url = args.value or config.plugin_catalog_url
            if not url:
                raise ValueError("catalog URL required")
            print(json.dumps(fetch_catalog(url), ensure_ascii=False, indent=2))
        elif args.action == "updates":
            config = load_config(args.config)
            url = args.value or config.plugin_catalog_url
            if not url:
                raise ValueError("catalog URL required")
            print(
                json.dumps(
                    available_updates(url, plugins_dir),
                    ensure_ascii=False,
                    indent=2,
                )
            )
        elif args.action == "simulate":
            if not args.value:
                raise ValueError("service id required")
            config = load_config(args.config)
            candidates = {
                plugin.manifest.service_id: plugin for plugin in load_plugins(plugins_dir)
            }
            plugin = candidates.get(args.value)
            if plugin is None:
                raise ValueError("installed plugin service not found")
            result = plugin.handler(args.input, config, None)
            print(result.title)
            print(result.body)
        return 0
    return 1


# Lance le programme asynchrone.
async def async_main(args: argparse.Namespace) -> None:
    from .core.bridge import FlipNetBridge
    from .core.config import load_config
    from .registry.runtime import SUPPORTED_SERVICES

    config = load_config(args.config)
    config.state_path = default_state_path()
    config.data_path = args.data_file
    logging.getLogger(__name__).info("FlipNet Bridge %s", __version__)
    logging.getLogger(__name__).info("Services: %s", ", ".join(SUPPORTED_SERVICES))
    logging.getLogger(__name__).info("Configuration: %s", args.config)
    logging.getLogger(__name__).info("Etat: %s", config.state_path)
    logging.getLogger(__name__).info("Donnees: %s", config.data_path)
    logging.getLogger(__name__).info("Transport: %s", args.transport)
    bridge = FlipNetBridge(
        config=config, transport=args.transport, device_address=args.device, usb_port=args.port
    )
    if args.once:
        await bridge.run_once()
    else:
        await bridge.run_forever()


# Lance le programme.
def main() -> None:
    args = build_parser().parse_args()
    if args.command is not None:
        try:
            raise SystemExit(run_catalog_command(args))
        except (DataValidationError, KeyError, OSError, ValueError) as exc:
            print(f"FlipNet data error: {exc}", file=sys.stderr)
            raise SystemExit(2) from exc
    log_file = configure_logging(
        args.debug,
        args.log_file,
        args.service_mode,
    )
    if log_file is not None:
        logging.getLogger(__name__).info("Journal: %s", log_file)
    try:
        asyncio.run(async_main(args))
    except KeyboardInterrupt:
        print("\nFlipNet Bridge arrete.")


if __name__ == "__main__":
    main()
