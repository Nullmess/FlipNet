#ifndef FLIPNET_FEATURES_GENERATED_H
# define FLIPNET_FEATURES_GENERATED_H

# include <stdbool.h>
# include <stddef.h>

# define FLIPNET_FEATURE_CAPABILITIES "capabilities"
# define FLIPNET_FEATURE_EXTENSION_MANAGER_LIST "extension_manager_list"
# define FLIPNET_FEATURE_EXTENSION_MANAGER_TOGGLE "extension_manager_toggle"
# define FLIPNET_FEATURE_PLUGIN_MANAGER_LIST "plugin_manager_list"
# define FLIPNET_FEATURE_PLUGIN_MANAGER_TOGGLE "plugin_manager_toggle"
# define FLIPNET_FEATURE_PLUGIN_MANAGER_REMOVE "plugin_manager_remove"
# define FLIPNET_FEATURE_HEADERS "headers"
# define FLIPNET_FEATURE_TLS "tls"
# define FLIPNET_FEATURE_DNS "dns"
# define FLIPNET_FEATURE_DOMAIN_AUDIT "domain_audit"
# define FLIPNET_FEATURE_ENDPOINT_CHECK "endpoint_check"
# define FLIPNET_FEATURE_PORT_SCAN "port_scan"
# define FLIPNET_FEATURE_HOST_DISCOVERY "host_discovery"
# define FLIPNET_FEATURE_INVENTORY_SCAN "inventory_scan"
# define FLIPNET_FEATURE_INVENTORY_SUMMARY "inventory_summary"
# define FLIPNET_FEATURE_INVENTORY_CHANGES "inventory_changes"
# define FLIPNET_FEATURE_HOST_STATUS "host_status"
# define FLIPNET_FEATURE_SECURITY_STATUS "security_status"
# define FLIPNET_FEATURE_SERVICES_STATUS "services_status"
# define FLIPNET_FEATURE_NETWORK_GUARD "network_guard"
# define FLIPNET_FEATURE_WORKLOADS_STATUS "workloads_status"
# define FLIPNET_FEATURE_UPDATES_STATUS "updates_status"
# define FLIPNET_FEATURE_DASHBOARD "dashboard"
# define FLIPNET_FEATURE_TIMELINE "timeline"
# define FLIPNET_FEATURE_MONITORS "monitors"
# define FLIPNET_FEATURE_MONITOR_ADD "monitor_add"
# define FLIPNET_FEATURE_MONITOR_REMOVE "monitor_remove"
# define FLIPNET_FEATURE_MONITOR_RUN "monitor_run"
# define FLIPNET_FEATURE_ALERTS "alerts"
# define FLIPNET_FEATURE_ALERTS_CLEAR "alerts_clear"
# define FLIPNET_FEATURE_PLAYBOOK_LIST "playbook_list"
# define FLIPNET_FEATURE_PLAYBOOK_PREVIEW "playbook_preview"
# define FLIPNET_FEATURE_PLAYBOOK_PREPARE "playbook_prepare"
# define FLIPNET_FEATURE_ACTION_PREPARE "action_prepare"
# define FLIPNET_FEATURE_ACTION_PENDING "action_pending"
# define FLIPNET_FEATURE_ACTION_COMMIT "action_commit"
# define FLIPNET_FEATURE_ACTION_CANCEL "action_cancel"
# define FLIPNET_FEATURE_ACTION_LOG "action_log"
# define FLIPNET_FEATURE_TARGETS "targets"
# define FLIPNET_FEATURE_TARGET_DETAIL "target_detail"
# define FLIPNET_FEATURE_GROUPS "groups"
# define FLIPNET_FEATURE_FAVORITES "favorites"
# define FLIPNET_FEATURE_FAVORITE_LAST "favorite_last"
# define FLIPNET_FEATURE_FAVORITE_RUN "favorite_run"
# define FLIPNET_FEATURE_HISTORY "history"
# define FLIPNET_FEATURE_HISTORY_LAST "history_last"
# define FLIPNET_FEATURE_HISTORY_CLEAR "history_clear"
# define FLIPNET_FEATURE_APP_SEARCH "app_search"
# define FLIPNET_FEATURE_APP_RUN "app_run"
# define FLIPNET_FEATURE_WEB_SEARCH "web_search"
# define FLIPNET_FEATURE_WEB_PAGE "web_page"
# define FLIPNET_FEATURE_WEB_URL "web_url"
# define FLIPNET_FEATURE_WEATHER "weather"
# define FLIPNET_FEATURE_WEATHER_SEARCH "weather_search"
# define FLIPNET_FEATURE_WEATHER_LOCATION "weather_location"
# define FLIPNET_FEATURE_WIKI "wiki"
# define FLIPNET_FEATURE_WIKI_SEARCH "wiki_search"
# define FLIPNET_FEATURE_WIKI_PAGE "wiki_page"
# define FLIPNET_FEATURE_HEADLINES "headlines"
# define FLIPNET_FEATURE_FEED_DIGEST "feed_digest"
# define FLIPNET_FEATURE_FEED_ARTICLE "feed_article"

typedef struct s_flipnet_feature_definition
{
	const char	*id;
	const char	*domain;
	const char	*group;
	const char	*kind;
	const char	*category;
	const char	*ui_mode;
	const char	*label_en;
	const char	*label_fr;
	const char	*prompt_en;
	const char	*prompt_fr;
	const char	*initial;
	const char	*next_service;
	const char	*menu_parent;
	const char	*menu_native;
	const char	*menu_argument;
	const char	*menu_label_en;
	const char	*menu_label_fr;
	unsigned char	menu_order;
	bool		visible;
	bool		menu_visible;
} t_flipnet_feature_definition;

extern const t_flipnet_feature_definition	g_flipnet_features[];
extern const size_t				g_flipnet_features_count;

const t_flipnet_feature_definition	*flipnet_feature_definition_find(
			const char *service);

#endif
