#include "flipnet_menu.h"

static void	flipnet_menu_free_primary(t_flipnet_app *app)
{
	if (app->internet)
		submenu_free(app->internet);
	if (app->ports)
		submenu_free(app->ports);
	if (app->discovery)
		submenu_free(app->discovery);
	if (app->network)
		submenu_free(app->network);
	if (app->home)
		submenu_free(app->home);
	if (app->language_menu)
		submenu_free(app->language_menu);
}

static void	flipnet_menu_free_system(t_flipnet_app *app)
{
	if (app->operations)
		submenu_free(app->operations);
	if (app->monitors)
		submenu_free(app->monitors);
	if (app->actions)
		submenu_free(app->actions);
	if (app->systems)
		submenu_free(app->systems);
	if (app->security)
		submenu_free(app->security);
}

static void	flipnet_menu_free_content(t_flipnet_app *app)
{
	if (app->app_choices)
		submenu_free(app->app_choices);
	if (app->apps)
		submenu_free(app->apps);
	if (app->web_results)
		submenu_free(app->web_results);
	if (app->web)
		submenu_free(app->web);
	if (app->targets)
		submenu_free(app->targets);
	if (app->workspace)
		submenu_free(app->workspace);
	if (app->feeds_en)
		submenu_free(app->feeds_en);
	if (app->feeds_fr)
		submenu_free(app->feeds_fr);
	if (app->feeds)
		submenu_free(app->feeds);
}

static void	flipnet_menu_free_settings(t_flipnet_app *app)
{
	if (app->plugin_confirm)
		submenu_free(app->plugin_confirm);
	if (app->plugin_actions)
		submenu_free(app->plugin_actions);
	if (app->plugin_settings)
		submenu_free(app->plugin_settings);
	if (app->extension_settings)
		submenu_free(app->extension_settings);
	if (app->settings)
		submenu_free(app->settings);
	if (app->wiki_results)
		submenu_free(app->wiki_results);
	if (app->weather_results)
		submenu_free(app->weather_results);
	if (app->utilities)
		submenu_free(app->utilities);
}

void	flipnet_menus_free(t_flipnet_app *app)
{
	flipnet_menu_free_settings(app);
	flipnet_menu_free_content(app);
	flipnet_menu_free_system(app);
	flipnet_menu_free_primary(app);
}
