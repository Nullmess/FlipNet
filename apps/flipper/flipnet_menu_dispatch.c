#include "flipnet_i18n.h"
#include "flipnet_menu.h"

void	flipnet_menu_home_action(t_flipnet_app *app, uint32_t index);
void	flipnet_menu_network_action(t_flipnet_app *app, uint32_t index);
void	flipnet_menu_operations_action(t_flipnet_app *app, uint32_t index);
void	flipnet_menu_feeds_action(t_flipnet_app *app, uint32_t index);
void	flipnet_menu_feeds_fr_action(t_flipnet_app *app, uint32_t index);
void	flipnet_menu_feeds_en_action(t_flipnet_app *app, uint32_t index);
void	flipnet_menu_settings_action(t_flipnet_app *app, uint32_t index);
void	flipnet_extension_settings_callback(void *context, uint32_t index);
void	flipnet_plugin_settings_callback(void *context, uint32_t index);
void	flipnet_plugin_action_callback(void *context, uint32_t index);
void	flipnet_plugin_confirm_callback(void *context, uint32_t index);

static bool	flipnet_menu_primary_dispatch(t_flipnet_app *app,
			uint32_t index)
{
	if (app->current_view == VIEW_LANGUAGE)
		flipnet_language_choose(app, index);
	else if (app->current_view == VIEW_HOME)
		flipnet_menu_home_action(app, index);
	else if (app->current_view == VIEW_NETWORK)
		flipnet_menu_network_action(app, index);
	else if (app->current_view == VIEW_OPERATIONS)
		flipnet_menu_operations_action(app, index);
	else if (app->current_view == VIEW_FEEDS)
		flipnet_menu_feeds_action(app, index);
	else if (app->current_view == VIEW_FEEDS_FR)
		flipnet_menu_feeds_fr_action(app, index);
	else if (app->current_view == VIEW_FEEDS_EN)
		flipnet_menu_feeds_en_action(app, index);
	else
		return (false);
	return (true);
}

static void	flipnet_menu_settings_dispatch(t_flipnet_app *app,
			uint32_t index)
{
	if (app->current_view == VIEW_SETTINGS)
		flipnet_menu_settings_action(app, index);
	else if (app->current_view == VIEW_EXTENSION_SETTINGS)
		flipnet_extension_settings_callback(app, index);
	else if (app->current_view == VIEW_PLUGIN_SETTINGS)
		flipnet_plugin_settings_callback(app, index);
	else if (app->current_view == VIEW_PLUGIN_ACTIONS)
		flipnet_plugin_action_callback(app, index);
	else if (app->current_view == VIEW_PLUGIN_CONFIRM)
		flipnet_plugin_confirm_callback(app, index);
}

void	flipnet_menu_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (!flipnet_menu_primary_dispatch(app, index))
		flipnet_menu_settings_dispatch(app, index);
}
