#include "flipnet_core.h"

static t_flipnet_view	flipnet_parent_network(t_flipnet_app *app)
{
	if (app->current_view == VIEW_FEEDS_FR
		|| app->current_view == VIEW_FEEDS_EN)
		return (VIEW_FEEDS);
	if (app->current_view == VIEW_FEED_CARDS)
		return (app->feed_parent);
	if (app->current_view == VIEW_WEB_RESULTS
		|| app->current_view == VIEW_WEATHER_RESULTS
		|| app->current_view == VIEW_WIKI_RESULTS)
		return (VIEW_INTERNET);
	if (app->current_view == VIEW_DISCOVERY
		|| app->current_view == VIEW_PORTS
		|| app->current_view == VIEW_SECURITY)
		return (VIEW_NETWORK);
	if (app->current_view == VIEW_ACTIONS
		|| app->current_view == VIEW_MONITORS)
		return (VIEW_OPERATIONS);
	return (VIEW_HOME);
}

static t_flipnet_view	flipnet_parent_tools(t_flipnet_app *app)
{
	if (app->current_view == VIEW_APP_CHOICES
		|| app->current_view == VIEW_APPS)
		return (VIEW_UTILITIES);
	if (app->current_view == VIEW_FEEDS
		|| app->current_view == VIEW_WEB)
		return (VIEW_INTERNET);
	if (app->current_view == VIEW_TARGETS)
		return (VIEW_WORKSPACE);
	if (app->current_view == VIEW_EXTENSION_SETTINGS)
		return (VIEW_SETTINGS);
	if (app->current_view == VIEW_PLUGIN_SETTINGS)
		return (VIEW_HOME);
	if (app->current_view == VIEW_PLUGIN_ACTIONS)
		return (VIEW_PLUGIN_SETTINGS);
	if (app->current_view == VIEW_PLUGIN_CONFIRM)
		return (VIEW_PLUGIN_ACTIONS);
	return (VIEW_HOME);
}

static t_flipnet_view	flipnet_parent(t_flipnet_app *app)
{
	t_flipnet_view	parent;

	if (app->current_view == VIEW_LANGUAGE)
	{
		if (app->language == LANGUAGE_UNSET)
			return (VIEW_LANGUAGE);
		return (VIEW_SETTINGS);
	}
	if (app->current_view == VIEW_INPUT || app->current_view == VIEW_RESULT)
		return (app->return_view);
	parent = flipnet_parent_network(app);
	if (parent != VIEW_HOME)
		return (parent);
	return (flipnet_parent_tools(app));
}

bool	flipnet_navigation(void *context)
{
	t_flipnet_app	*app;
	t_flipnet_view	parent;

	app = context;
	if (app->current_view == VIEW_HOME
		|| (app->current_view == VIEW_LANGUAGE
			&& app->language == LANGUAGE_UNSET))
	{
		view_dispatcher_stop(app->dispatcher);
		return (true);
	}
	if (app->current_view == VIEW_LOADING
		|| app->current_view == VIEW_OVERVIEW)
		flipnet_cancel(app);
	parent = flipnet_parent(app);
	flipnet_switch(app, parent);
	return (true);
}
