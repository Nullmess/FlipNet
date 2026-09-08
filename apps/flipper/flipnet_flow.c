#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

static void	flipnet_flow_notify(t_flipnet_app *app, t_flipnet_card kind)
{
	if (kind == CARD_SUCCESS)
		notification_message(app->notification, &sequence_success);
	else
		notification_message(app->notification, &sequence_error);
}

void	flipnet_flow_begin(t_flipnet_app *app)
{
	if (app->request_silent)
	{
		flipnet_background_start(app);
		return ;
	}
	flipnet_switch(app, VIEW_LOADING);
	flipnet_animation_start(app);
}

void	flipnet_flow_stop(t_flipnet_app *app)
{
	app->pending_request = 0U;
	app->pending_transport = TRANSPORT_NONE;
	app->waiting_transport = false;
	app->request_silent = false;
	flipnet_animation_stop(app);
}

void	flipnet_flow_result(t_flipnet_app *app, const char *title,
			const char *body, t_flipnet_card kind)
{
	flipnet_flow_stop(app);
	flipnet_flow_notify(app, kind);
	flipnet_result_set_kind(app, title, body, kind);
	flipnet_switch(app, VIEW_RESULT);
}

void	flipnet_flow_overview(t_flipnet_app *app, const char *body)
{
	flipnet_flow_stop(app);
	if (!body || !flipnet_overview_update(app, body))
		flipnet_overview_failed(app);
	flipnet_switch(app, VIEW_OVERVIEW);
}

