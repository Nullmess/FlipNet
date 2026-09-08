#include "flipnet_menu.h"

Submenu	*flipnet_menu_language(t_flipnet_app *app);
Submenu	*flipnet_menu_home(t_flipnet_app *app);
Submenu	*flipnet_menu_internet(t_flipnet_app *app);
Submenu	*flipnet_menu_network(t_flipnet_app *app);
Submenu	*flipnet_menu_discovery(t_flipnet_app *app);
Submenu	*flipnet_menu_ports(t_flipnet_app *app);
Submenu	*flipnet_menu_security(t_flipnet_app *app);
Submenu	*flipnet_menu_systems(t_flipnet_app *app);
Submenu	*flipnet_menu_operations(t_flipnet_app *app);
Submenu	*flipnet_menu_actions(t_flipnet_app *app);
Submenu	*flipnet_menu_monitors(t_flipnet_app *app);
Submenu	*flipnet_menu_feeds(t_flipnet_app *app);
Submenu	*flipnet_menu_feeds_fr(t_flipnet_app *app);
Submenu	*flipnet_menu_feeds_en(t_flipnet_app *app);
Submenu	*flipnet_menu_web(t_flipnet_app *app);
Submenu	*flipnet_menu_workspace(t_flipnet_app *app);
Submenu	*flipnet_menu_targets(t_flipnet_app *app);
Submenu	*flipnet_menu_utilities(t_flipnet_app *app);
Submenu	*flipnet_menu_settings(t_flipnet_app *app);
void	flipnet_menu_refresh_primary(t_flipnet_app *app);
void	flipnet_menu_refresh_secondary(t_flipnet_app *app);
void	flipnet_menu_refresh_content(t_flipnet_app *app);
void	flipnet_menu_refresh_settings(t_flipnet_app *app);

void	flipnet_menu_alloc_primary(t_flipnet_app *app);
void	flipnet_menu_alloc_system(t_flipnet_app *app);
void	flipnet_menu_alloc_content(t_flipnet_app *app);
void	flipnet_menu_alloc_settings(t_flipnet_app *app);

void	flipnet_menu_alloc_primary(t_flipnet_app *app)
{
	app->language_menu = flipnet_menu_language(app);
	app->home = flipnet_menu_home(app);
	app->internet = flipnet_menu_internet(app);
	app->network = flipnet_menu_network(app);
	app->discovery = flipnet_menu_discovery(app);
	app->ports = flipnet_menu_ports(app);
}

void	flipnet_menu_alloc_system(t_flipnet_app *app)
{
	app->security = flipnet_menu_security(app);
	app->systems = flipnet_menu_systems(app);
	app->operations = flipnet_menu_operations(app);
	app->actions = flipnet_menu_actions(app);
	app->monitors = flipnet_menu_monitors(app);
}

void	flipnet_menu_alloc_content(t_flipnet_app *app)
{
	app->feeds = flipnet_menu_feeds(app);
	app->feeds_fr = flipnet_menu_feeds_fr(app);
	app->feeds_en = flipnet_menu_feeds_en(app);
	app->web = flipnet_menu_web(app);
	app->web_results = flipnet_submenu("Choose result");
	app->apps = flipnet_submenu("Online apps");
	app->app_choices = flipnet_submenu("Choose");
	app->workspace = flipnet_menu_workspace(app);
	app->targets = flipnet_menu_targets(app);
}

void	flipnet_menu_alloc_settings(t_flipnet_app *app)
{
	app->utilities = flipnet_menu_utilities(app);
	app->weather_results = flipnet_submenu("Choose city");
	app->wiki_results = flipnet_submenu("Choose article");
	app->settings = flipnet_menu_settings(app);
	app->extension_settings = flipnet_submenu("Extensions");
	app->plugin_settings = flipnet_submenu("Installed plugins");
	app->plugin_actions = flipnet_submenu("Plugin");
	app->plugin_confirm = flipnet_submenu("Remove plugin");
}

bool	flipnet_menus_alloc(t_flipnet_app *app)
{
	flipnet_menu_alloc_primary(app);
	flipnet_menu_alloc_system(app);
	flipnet_menu_alloc_content(app);
	flipnet_menu_alloc_settings(app);
	if (!app->language_menu || !app->home || !app->internet
		|| !app->network || !app->discovery || !app->ports)
		return (false);
	if (!app->security || !app->systems || !app->operations
		|| !app->actions || !app->monitors)
		return (false);
	if (!app->feeds || !app->feeds_fr || !app->feeds_en || !app->web
		|| !app->web_results || !app->apps || !app->app_choices
		|| !app->workspace)
		return (false);
	if (!app->targets)
		return (false);
	return (app->utilities && app->weather_results && app->wiki_results
		&& app->settings && app->extension_settings && app->plugin_settings
		&& app->plugin_actions && app->plugin_confirm);
}
