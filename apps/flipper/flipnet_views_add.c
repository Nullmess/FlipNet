#include "flipnet_menu.h"

static void	flipnet_views_add_primary(t_flipnet_app *app)
{
	view_dispatcher_add_view(app->dispatcher, VIEW_LANGUAGE,
		submenu_get_view(app->language_menu));
	view_dispatcher_add_view(app->dispatcher, VIEW_HOME,
		submenu_get_view(app->home));
	view_dispatcher_add_view(app->dispatcher, VIEW_OVERVIEW, app->overview);
	view_dispatcher_add_view(app->dispatcher, VIEW_INTERNET,
		submenu_get_view(app->internet));
	view_dispatcher_add_view(app->dispatcher, VIEW_NETWORK,
		submenu_get_view(app->network));
	view_dispatcher_add_view(app->dispatcher, VIEW_DISCOVERY,
		submenu_get_view(app->discovery));
	view_dispatcher_add_view(app->dispatcher, VIEW_PORTS,
		submenu_get_view(app->ports));
}

static void	flipnet_views_add_system(t_flipnet_app *app)
{
	view_dispatcher_add_view(app->dispatcher, VIEW_SECURITY,
		submenu_get_view(app->security));
	view_dispatcher_add_view(app->dispatcher, VIEW_SYSTEMS,
		submenu_get_view(app->systems));
	view_dispatcher_add_view(app->dispatcher, VIEW_OPERATIONS,
		submenu_get_view(app->operations));
	view_dispatcher_add_view(app->dispatcher, VIEW_ACTIONS,
		submenu_get_view(app->actions));
	view_dispatcher_add_view(app->dispatcher, VIEW_MONITORS,
		submenu_get_view(app->monitors));
}

static void	flipnet_views_add_content(t_flipnet_app *app)
{
	view_dispatcher_add_view(app->dispatcher, VIEW_FEEDS,
		submenu_get_view(app->feeds));
	view_dispatcher_add_view(app->dispatcher, VIEW_FEEDS_FR,
		submenu_get_view(app->feeds_fr));
	view_dispatcher_add_view(app->dispatcher, VIEW_FEEDS_EN,
		submenu_get_view(app->feeds_en));
	view_dispatcher_add_view(app->dispatcher, VIEW_FEED_CARDS,
		app->feed_cards);
	view_dispatcher_add_view(app->dispatcher, VIEW_WEB,
		submenu_get_view(app->web));
	view_dispatcher_add_view(app->dispatcher, VIEW_WEB_RESULTS,
		submenu_get_view(app->web_results));
	view_dispatcher_add_view(app->dispatcher, VIEW_APPS,
		submenu_get_view(app->apps));
	view_dispatcher_add_view(app->dispatcher, VIEW_APP_CHOICES,
		submenu_get_view(app->app_choices));
	view_dispatcher_add_view(app->dispatcher, VIEW_WORKSPACE,
		submenu_get_view(app->workspace));
	view_dispatcher_add_view(app->dispatcher, VIEW_TARGETS,
		submenu_get_view(app->targets));
}

static void	flipnet_views_add_final(t_flipnet_app *app)
{
	view_dispatcher_add_view(app->dispatcher, VIEW_UTILITIES,
		submenu_get_view(app->utilities));
	view_dispatcher_add_view(app->dispatcher, VIEW_WEATHER_RESULTS,
		submenu_get_view(app->weather_results));
	view_dispatcher_add_view(app->dispatcher, VIEW_WIKI_RESULTS,
		submenu_get_view(app->wiki_results));
	view_dispatcher_add_view(app->dispatcher, VIEW_SETTINGS,
		submenu_get_view(app->settings));
	view_dispatcher_add_view(app->dispatcher, VIEW_EXTENSION_SETTINGS,
		submenu_get_view(app->extension_settings));
	view_dispatcher_add_view(app->dispatcher, VIEW_PLUGIN_SETTINGS,
		submenu_get_view(app->plugin_settings));
	view_dispatcher_add_view(app->dispatcher, VIEW_PLUGIN_ACTIONS,
		submenu_get_view(app->plugin_actions));
	view_dispatcher_add_view(app->dispatcher, VIEW_PLUGIN_CONFIRM,
		submenu_get_view(app->plugin_confirm));
	view_dispatcher_add_view(app->dispatcher, VIEW_LOADING, app->loading);
	view_dispatcher_add_view(app->dispatcher, VIEW_INPUT,
		text_input_get_view(app->input));
	view_dispatcher_add_view(app->dispatcher, VIEW_RESULT, app->result);
}

void	flipnet_views_add(t_flipnet_app *app)
{
	flipnet_views_add_primary(app);
	flipnet_views_add_system(app);
	flipnet_views_add_content(app);
	flipnet_views_add_final(app);
}
