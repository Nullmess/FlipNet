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

void	flipnet_response_error(t_flipnet_app *app,
			t_flipnet_response *response)
{
	t_flipnet_error_result	result;

	if (response->status != FLIPNET_RESPONSE_ERROR)
		return ;
	if (!flipnet_error_protocol_apply(response->body, sizeof(response->body),
			app->language == LANGUAGE_FR, &result))
		return ;
	flipnet_copy(app->last_error, sizeof(app->last_error), result.code);
}

void	flipnet_response_process(t_flipnet_app *app,
			t_flipnet_response *response)
{
	flipnet_response_error(app, response);
	if (response->request_id == 0U)
		flipnet_response_alert(app, response);
	else
		flipnet_response_regular(app, response);
}
