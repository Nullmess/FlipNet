#include "flipnet_core.h"
#include "flipnet_ui.h"

void	flipnet_error_show(t_flipnet_app *app, const char *title,
			const char *message)
{
	flipnet_copy(app->last_error, sizeof(app->last_error), title);
	flipnet_flow_result(app, title, message, CARD_FAILURE);
}
