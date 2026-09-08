"""Associe les identifiants générés aux gestionnaires Python."""

from __future__ import annotations

from ..core.config import BridgeConfig
from ..registry.api import FeatureHandler, stateful, stateless
from . import workspace, management, network, operations, systems
from .extensions import feeds, knowledge, utilities, web
from .shared import ServiceResult


def capabilities(argument: str, config: BridgeConfig, state) -> ServiceResult:
    """Construit le catalogue des capacités."""

    del state
    from ..registry.runtime import capabilities_payload

    return ServiceResult("Capabilities", capabilities_payload(config, argument=argument))


def builtin_handlers() -> dict[str, FeatureHandler]:
    """Retourne toutes les associations intégrées."""

    return {
        "capabilities": capabilities,
        "extension_manager_list": stateless(management.extension_manager_list),
        "extension_manager_toggle": stateless(management.extension_manager_toggle),
        "plugin_manager_list": stateless(management.plugin_manager_list),
        "plugin_manager_toggle": stateless(management.plugin_manager_toggle),
        "plugin_manager_remove": stateless(management.plugin_manager_remove),
        "dashboard": stateful(operations.dashboard),
        "timeline": stateful(operations.timeline_list),
        "targets": stateful(workspace.targets_list),
        "target_detail": stateful(workspace.target_detail),
        "groups": stateful(workspace.groups_list),
        "favorites": stateful(workspace.favorites_list),
        "favorite_last": stateful(workspace.favorite_last),
        "favorite_run": stateful(workspace.favorite_run),
        "history": stateful(workspace.history_list),
        "history_last": stateful(workspace.history_last),
        "history_clear": stateful(workspace.history_clear),
        "monitors": stateful(operations.monitors_list),
        "monitor_add": stateful(operations.monitor_add),
        "monitor_remove": stateful(operations.monitor_remove),
        "monitor_run": stateful(operations.monitor_run),
        "alerts": stateful(operations.alerts_list),
        "alerts_clear": stateful(operations.alerts_clear),
        "playbook_list": stateful(operations.playbook_list),
        "playbook_preview": stateful(operations.playbook_preview),
        "playbook_prepare": stateful(operations.playbook_prepare),
        "action_prepare": stateful(operations.action_prepare),
        "action_pending": stateful(operations.action_pending),
        "action_commit": stateful(operations.action_commit),
        "action_cancel": stateful(operations.action_cancel),
        "action_log": stateful(operations.action_log),
        "app_search": stateless(utilities.app_search),
        "app_run": stateless(utilities.app_run),
        "web_search": stateful(web.web_search),
        "web_page": stateful(web.web_page),
        "web_url": stateful(web.web_url),
        "weather": stateless(knowledge.weather),
        "weather_search": stateless(knowledge.weather_search),
        "weather_location": stateless(knowledge.weather_location),
        "wiki": stateless(knowledge.wikipedia),
        "wiki_search": stateless(knowledge.wikipedia_search),
        "wiki_page": stateless(knowledge.wikipedia_page),
        "headlines": stateless(feeds.headlines),
        "feed_digest": stateless(feeds.feed_digest),
        "feed_article": stateless(feeds.feed_article),
        "headers": stateless(network.http_security),
        "tls": stateless(network.tls_inspect),
        "dns": stateless(network.dns_lookup),
        "domain_audit": stateless(network.domain_audit),
        "endpoint_check": stateless(network.endpoint_check),
        "port_scan": stateless(network.port_scan),
        "host_discovery": stateless(network.host_discovery),
        "inventory_scan": stateful(network.inventory_scan),
        "inventory_summary": stateful(network.inventory_summary),
        "inventory_changes": stateful(network.inventory_changes),
        "host_status": stateless(systems.host_status),
        "security_status": stateless(systems.security_status),
        "services_status": stateless(systems.services_status),
        "network_guard": stateless(systems.network_guard),
        "workloads_status": stateless(systems.workloads_status),
        "updates_status": stateless(systems.updates_status),
    }
