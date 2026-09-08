#include "flipnet_features_generated.h"
#include "flipnet_capabilities_i.h"

#include <string.h>

void	flipnet_capabilities_apps(t_flipnet_app *app)
{
	const t_flipnet_remote_app	*source;
	uint8_t				index;
	bool					french;

	app->app_count = 0U;
	if (!app->capabilities || !app->capabilities->loaded)
		return ;
	french = app->language == LANGUAGE_FR;
	index = 0U;
	while (index < app->capabilities->app_count
		&& index < FLIPNET_APP_ITEMS)
	{
		source = &app->capabilities->apps[index];
		flipnet_capabilities_copy_app(app, source, index, french);
		app->app_count++;
		index++;
	}
}

void	flipnet_capabilities_load(t_flipnet_app *app)
{
	t_flipnet_capability_state	*state;

	if (!app)
		return ;
	state = flipnet_capabilities_state();
	if (!state->initialized)
	{
		flipnet_capability_catalog_init(&state->current);
		flipnet_capability_catalog_init(&state->pending);
		state->initialized = true;
	}
	app->capabilities = &state->current;
	app->catalog_transport = state->transport;
	flipnet_capabilities_apps(app);
}

t_flipnet_catalog_result	flipnet_capabilities_update(t_flipnet_app *app,
			const char *payload)
{
	t_flipnet_capability_state	*state;
	t_flipnet_catalog_result	result;

	if (!app)
		return (FLIPNET_CATALOG_INVALID);
	state = flipnet_capabilities_state();
	result = flipnet_capability_catalog_parse(&state->pending, payload);
	if (result != FLIPNET_CATALOG_COMPLETE)
		return (result);
	state->current = state->pending;
	state->transport = app->pending_transport;
	app->catalog_transport = state->transport;
	app->capabilities = &state->current;
	flipnet_capabilities_apps(app);
	return (FLIPNET_CATALOG_COMPLETE);
}

uint8_t	flipnet_capabilities_next_page(void)
{
	t_flipnet_capability_state	*state;

	state = flipnet_capabilities_state();
	return (state->pending.next_page);
}

bool	flipnet_has_service(const t_flipnet_app *app, const char *service)
{
	const t_flipnet_feature_definition	*feature;

	if (!app || !service)
		return (false);
	if (app->capabilities && app->capabilities->loaded)
		return (flipnet_capability_catalog_has(app->capabilities, service));
	feature = flipnet_feature_definition_find(service);
	return (feature && feature->menu_visible);
}
