#include "flipnet_core.h"
#include "flipnet_menu.h"

#include <string.h>

void	flipnet_copy(char *destination, size_t size, const char *source)
{
	size_t	length;

	if (!destination || size == 0U)
		return ;
	if (!source)
	{
		destination[0] = '\0';
		return ;
	}
	length = 0U;
	while (source[length] && length + 1U < size)
		length++;
	memcpy(destination, source, length);
	destination[length] = '\0';
}

void	flipnet_switch(t_flipnet_app *app, t_flipnet_view view)
{
	if (view == VIEW_HOME && app->catalog_refresh_pending
		&& !app->animation)
	{
		app->catalog_refresh_pending = false;
		flipnet_menus_refresh(app);
	}
	app->current_view = view;
	view_dispatcher_switch_to_view(app->dispatcher, view);
}

void	flipnet_cancel(t_flipnet_app *app)
{
	app->pending_request = 0U;
	app->pending_transport = TRANSPORT_NONE;
	app->waiting_transport = false;
	app->request_silent = false;
	flipnet_animation_stop(app);
}
