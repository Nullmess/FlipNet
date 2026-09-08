#include "flipnet_core.h"
#include "flipnet_menu.h"
#include "flipnet_files.h"

#include <stdlib.h>

static void	flipnet_app_free_transports(t_flipnet_app *app)
{
	if (app->usb)
		flipnet_usb_free(app->usb);
	if (app->ble)
		flipnet_ble_free(app->ble);
}

static void	flipnet_app_free_views(t_flipnet_app *app)
{
	if (app->views_added)
		flipnet_views_remove(app);
	if (app->result)
		view_free(app->result);
	if (app->input)
		text_input_free(app->input);
	if (app->feed_cards)
		view_free(app->feed_cards);
	if (app->loading)
		view_free(app->loading);
	if (app->overview)
		view_free(app->overview);
	flipnet_menus_free(app);
}

static void	flipnet_app_free_lines(t_flipnet_app *app)
{
	char	*line;

	if (!app->lines)
		return ;
	line = NULL;
	while (furi_message_queue_get(app->lines, &line, 0U)
		== FuriStatusOk)
	{
		free(line);
		line = NULL;
	}
	furi_message_queue_free(app->lines);
}

static void	flipnet_app_free_records(t_flipnet_app *app)
{
	if (app->dispatcher)
		view_dispatcher_free(app->dispatcher);
	if (app->notification)
		furi_record_close(RECORD_NOTIFICATION);
	if (app->gui)
		furi_record_close(RECORD_GUI);
	flipnet_app_free_lines(app);
}

void	flipnet_app_free(t_flipnet_app *app)
{
	if (!app)
		return ;
	flipnet_animation_stop(app);
	flipnet_files_reset(app);
	if (app->timer)
		furi_timer_free(app->timer);
	flipnet_app_free_transports(app);
	flipnet_app_free_views(app);
	flipnet_app_free_records(app);
	free(app);
}
