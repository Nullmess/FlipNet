#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

bool	flipnet_flow_feed(t_flipnet_app *app, const char *body)
{
	if (!body || !flipnet_feed_update(app, body))
		return (false);
	flipnet_flow_stop(app);
	app->feed_parent = app->return_view;
	flipnet_switch(app, VIEW_FEED_CARDS);
	return (true);
}

void	flipnet_flow_timeout(t_flipnet_app *app)
{
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_CAPABILITIES))
	{
		bool	silent;

		silent = app->request_silent;
		flipnet_flow_stop(app);
		if (silent)
			app->capabilities_attempted = false;
		if (!silent)
			flipnet_switch(app, app->return_view);
		return ;
	}
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_DASHBOARD))
	{
		flipnet_flow_overview(app, NULL);
		return ;
	}
	flipnet_flow_result(app,
		flipnet_tr(app, "Request timeout", "Delai depasse"),
		flipnet_tr(app,
			"The bridge did not answer.\nCheck the gateway and try again.",
			"Le bridge ne repond pas.\nVerifie le gateway et reessaie."),
		CARD_FAILURE);
}
