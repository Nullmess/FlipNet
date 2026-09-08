#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_ui.h"

#include <stdio.h>
#include <string.h>

void	flipnet_response_catalog_complete(t_flipnet_app *app,
			t_flipnet_view parent, bool silent);

static void	flipnet_response_catalog_more(t_flipnet_app *app,
			t_flipnet_view parent, bool silent)
{
	char	argument[8];

	snprintf(argument, sizeof(argument), "2|%u",
		(unsigned int)flipnet_capabilities_next_page());
	flipnet_flow_stop(app);
	if (silent)
		flipnet_request_direct_background(app, parent,
			FLIPNET_FEATURE_CAPABILITIES, argument);
	else
		flipnet_request_direct(app, parent,
			FLIPNET_FEATURE_CAPABILITIES, argument);
}

static void	flipnet_response_catalog_end(t_flipnet_app *app,
			t_flipnet_view parent, bool silent, t_flipnet_catalog_result result)
{
	flipnet_flow_stop(app);
	if (result == FLIPNET_CATALOG_COMPLETE)
		flipnet_response_catalog_complete(app, parent, silent);
	else if (silent)
		app->capabilities_attempted = false;
	else
		flipnet_switch(app, parent);
}

bool	flipnet_response_catalog(t_flipnet_app *app,
			t_flipnet_response *response)
{
	t_flipnet_catalog_result	result;
	t_flipnet_view			parent;
	bool					silent;

	if (strcmp(app->pending_service, FLIPNET_FEATURE_CAPABILITIES))
		return (false);
	parent = app->return_view;
	silent = app->request_silent;
	result = FLIPNET_CATALOG_INVALID;
	if (response->status != FLIPNET_RESPONSE_ERROR)
		result = flipnet_capabilities_update(app, response->body);
	if (result == FLIPNET_CATALOG_MORE)
		flipnet_response_catalog_more(app, parent, silent);
	else
		flipnet_response_catalog_end(app, parent, silent, result);
	return (true);
}
