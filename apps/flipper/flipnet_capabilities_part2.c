#include "flipnet_features_generated.h"
#include "flipnet_capabilities_i.h"

bool	flipnet_has_any_service(const t_flipnet_app *app,
			const char *const *services, size_t count)
{
	size_t	index;

	index = 0U;
	while (index < count)
	{
		if (flipnet_has_service(app, services[index]))
			return (true);
		index++;
	}
	return (false);
}

void	flipnet_capabilities_invalidate(t_flipnet_app *app)
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
	flipnet_capability_catalog_init(&state->current);
	flipnet_capability_catalog_init(&state->pending);
	app->capabilities = &state->current;
	app->catalog_transport = TRANSPORT_NONE;
	state->transport = TRANSPORT_NONE;
	app->capabilities_attempted = false;
	app->app_count = 0U;
}

void	flipnet_capabilities_prepare(t_flipnet_app *app)
{
	t_flipnet_capability_state	*state;

	state = flipnet_capabilities_state();
	flipnet_capability_catalog_init(&state->pending);
	app->capabilities_attempted = true;
}

void	flipnet_capabilities_refresh(t_flipnet_app *app)
{
	if (!app)
		return ;
	flipnet_capabilities_prepare(app);
	flipnet_request_direct(app, app->current_view,
		FLIPNET_FEATURE_CAPABILITIES, "2|0");
}

void	flipnet_capabilities_refresh_background(t_flipnet_app *app)
{
	if (!app || app->animation)
		return ;
	flipnet_capabilities_prepare(app);
	flipnet_request_direct_background(app, app->current_view,
		FLIPNET_FEATURE_CAPABILITIES, "2|0");
}
