#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_settings_manager.h"
#include "flipnet_ui.h"

#include <string.h>

static bool	flipnet_response_apps(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_APP_SEARCH))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR
		&& !flipnet_apps_choices_show(app, response->body))
		flipnet_flow_result(app, flipnet_tr(app, "Apps", "Applications"),
			flipnet_tr(app, "Invalid choice list", "Liste de choix invalide"),
			CARD_FAILURE);
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

static bool	flipnet_response_extensions(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_EXTENSION_MANAGER_LIST))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR
		&& !flipnet_extension_settings_show(app, response->body))
		flipnet_flow_result(app, "Extensions",
			flipnet_tr(app, "Invalid extension catalog",
				"Catalogue des extensions invalide"), CARD_FAILURE);
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

static bool	flipnet_response_plugins(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_PLUGIN_MANAGER_LIST))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR
		&& !flipnet_plugin_settings_show(app, response->body))
		flipnet_flow_result(app, "Plugins",
			flipnet_tr(app, "Invalid plugin catalog",
				"Catalogue des plugins invalide"), CARD_FAILURE);
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

static bool	flipnet_response_management_change(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_EXTENSION_MANAGER_TOGGLE)
		&& strcmp(app->pending_service, FLIPNET_FEATURE_PLUGIN_MANAGER_TOGGLE)
		&& strcmp(app->pending_service, FLIPNET_FEATURE_PLUGIN_MANAGER_REMOVE))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR)
		flipnet_settings_management_changed(app);
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

bool	flipnet_response_managers(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (flipnet_response_apps(app, response)
		|| flipnet_response_extensions(app, response)
		|| flipnet_response_plugins(app, response))
		return (true);
	return (flipnet_response_management_change(app, response));
}
