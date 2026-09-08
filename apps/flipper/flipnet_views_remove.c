#include "flipnet_menu.h"

static void	flipnet_views_remove_primary(t_flipnet_app *app)
{
	view_dispatcher_remove_view(app->dispatcher, VIEW_LANGUAGE);
	view_dispatcher_remove_view(app->dispatcher, VIEW_HOME);
	view_dispatcher_remove_view(app->dispatcher, VIEW_OVERVIEW);
	view_dispatcher_remove_view(app->dispatcher, VIEW_INTERNET);
	view_dispatcher_remove_view(app->dispatcher, VIEW_NETWORK);
	view_dispatcher_remove_view(app->dispatcher, VIEW_DISCOVERY);
	view_dispatcher_remove_view(app->dispatcher, VIEW_PORTS);
}

static void	flipnet_views_remove_system(t_flipnet_app *app)
{
	view_dispatcher_remove_view(app->dispatcher, VIEW_SECURITY);
	view_dispatcher_remove_view(app->dispatcher, VIEW_SYSTEMS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_OPERATIONS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_ACTIONS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_MONITORS);
}

static void	flipnet_views_remove_content(t_flipnet_app *app)
{
	view_dispatcher_remove_view(app->dispatcher, VIEW_FEEDS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_FEEDS_FR);
	view_dispatcher_remove_view(app->dispatcher, VIEW_FEEDS_EN);
	view_dispatcher_remove_view(app->dispatcher, VIEW_FEED_CARDS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_WEB_RESULTS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_APP_CHOICES);
	view_dispatcher_remove_view(app->dispatcher, VIEW_APPS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_WEB);
	view_dispatcher_remove_view(app->dispatcher, VIEW_TARGETS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_WORKSPACE);
}

static void	flipnet_views_remove_final(t_flipnet_app *app)
{
	view_dispatcher_remove_view(app->dispatcher, VIEW_UTILITIES);
	view_dispatcher_remove_view(app->dispatcher, VIEW_WEATHER_RESULTS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_WIKI_RESULTS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_PLUGIN_CONFIRM);
	view_dispatcher_remove_view(app->dispatcher, VIEW_PLUGIN_ACTIONS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_PLUGIN_SETTINGS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_EXTENSION_SETTINGS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_SETTINGS);
	view_dispatcher_remove_view(app->dispatcher, VIEW_LOADING);
	view_dispatcher_remove_view(app->dispatcher, VIEW_INPUT);
	view_dispatcher_remove_view(app->dispatcher, VIEW_RESULT);
}

void	flipnet_views_remove(t_flipnet_app *app)
{
	flipnet_views_remove_final(app);
	flipnet_views_remove_content(app);
	flipnet_views_remove_system(app);
	flipnet_views_remove_primary(app);
}
