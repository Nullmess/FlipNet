#include "flipnet_core.h"

#include <stdlib.h>
#include <string.h>

void	flipnet_line_callback(const char *line, void *context)
{
	t_flipnet_app	*app;
	char			*copy;
	size_t			length;

	app = context;
	if (!app || !line)
		return ;
	length = strlen(line);
	if (!length || length >= FLIPNET_PROTOCOL_MAX_LINE)
		return ;
	copy = malloc(length + 1U);
	if (!copy)
		return ;
	memcpy(copy, line, length + 1U);
	if (furi_message_queue_put(app->lines, &copy, 0U) != FuriStatusOk)
	{
		free(copy);
		return ;
	}
	view_dispatcher_send_custom_event(app->dispatcher, EVENT_LINE);
}

void	flipnet_ble_callback(bool connected, bool advertising, void *context)
{
	t_flipnet_app	*app;

	UNUSED(connected);
	UNUSED(advertising);
	app = context;
	view_dispatcher_send_custom_event(app->dispatcher, EVENT_TRANSPORT);
}

void	flipnet_usb_callback(bool connected, void *context)
{
	t_flipnet_app	*app;

	UNUSED(connected);
	app = context;
	view_dispatcher_send_custom_event(app->dispatcher, EVENT_TRANSPORT);
}
