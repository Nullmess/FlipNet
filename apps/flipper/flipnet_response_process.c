#include "flipnet_features_generated.h"
#include "flipnet_error_protocol.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"
#include "flipnet_settings_manager.h"
#include "flipnet_ui.h"

#include <stdio.h>
#include <string.h>

bool	flipnet_response_refresh_catalog(t_flipnet_app *app,
			t_flipnet_response *response);
void	flipnet_response_catalog_complete(t_flipnet_app *app,
			t_flipnet_view parent, bool silent);
bool	flipnet_response_feed(t_flipnet_app *app,
			t_flipnet_response *response);
void	flipnet_response_alert(t_flipnet_app *app,
			t_flipnet_response *response);
void	flipnet_response_regular(t_flipnet_app *app,
			t_flipnet_response *response);
void	flipnet_response_error(t_flipnet_app *app,
			t_flipnet_response *response);
bool	flipnet_response_catalog(t_flipnet_app *app,
			t_flipnet_response *response);
bool	flipnet_response_content(t_flipnet_app *app,
			t_flipnet_response *response);
bool	flipnet_response_managers(t_flipnet_app *app,
			t_flipnet_response *response);

bool	flipnet_response_refresh_catalog(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (response->status != FLIPNET_RESPONSE_ERROR
		|| !strcmp(app->pending_service, FLIPNET_FEATURE_CAPABILITIES)
		|| strcmp(app->last_error, "SERVICE_NOT_FOUND"))
		return (false);
	{
		t_flipnet_view	parent;

		parent = app->return_view;
		flipnet_flow_stop(app);
		flipnet_capabilities_invalidate(app);
		flipnet_switch(app, parent);
		flipnet_capabilities_refresh_background(app);
	}
	return (true);
}

void	flipnet_response_catalog_complete(t_flipnet_app *app,
			t_flipnet_view parent, bool silent)
{
	if (silent)
	{
		if (app->current_view == VIEW_HOME)
			flipnet_menus_refresh(app);
		else
			app->catalog_refresh_pending = true;
		return ;
	}
	flipnet_menus_refresh(app);
	if (flipnet_capabilities_resume_apps()
		&& flipnet_apps_open_cached(app))
		return ;
	flipnet_switch(app, parent);
}

bool	flipnet_response_feed(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_HEADLINES))
		return (true);
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_FEED_DIGEST))
		return (true);
	return (!strncmp(response->body, "FNFEED1\n", 8U));
}

void	flipnet_response_alert(t_flipnet_app *app,
			t_flipnet_response *response)
{
	notification_message(app->notification, &sequence_error);
	if (app->current_view == VIEW_INPUT || app->current_view == VIEW_RESULT
		|| app->current_view == VIEW_LOADING)
		return ;
	app->return_view = app->current_view;
	flipnet_result_set_kind(app, response->title, response->body, CARD_WARNING);
	flipnet_switch(app, VIEW_RESULT);
}

void	flipnet_response_regular(t_flipnet_app *app,
			t_flipnet_response *response)
{
	bool	feed;

	if (!app->pending_request
		|| response->request_id != app->pending_request)
		return ;
	feed = flipnet_response_feed(app, response);
	if (flipnet_response_catalog(app, response))
		return ;
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_DASHBOARD))
		flipnet_flow_overview(app, response->body);
	else if (flipnet_response_content(app, response))
		return ;
	else if (flipnet_response_managers(app, response))
		return ;
	else if (feed && flipnet_flow_feed(app, response->body))
		return ;
	else if (flipnet_response_refresh_catalog(app, response))
		return ;
	else if (response->status == FLIPNET_RESPONSE_ERROR)
		flipnet_flow_result(app, response->title, response->body, CARD_FAILURE);
	else
		flipnet_flow_result(app, response->title, response->body, CARD_SUCCESS);
}
