#include "flipnet_core.h"
#include "flipnet_menu.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <stdlib.h>
#include <string.h>

static bool	flipnet_app_base(t_flipnet_app *app)
{
	app->lines = furi_message_queue_alloc(FLIPNET_QUEUE_SIZE,
			sizeof(char *));
	app->timer = furi_timer_alloc(flipnet_animation_callback,
			FuriTimerTypePeriodic, app);
	app->gui = furi_record_open(RECORD_GUI);
	app->notification = furi_record_open(RECORD_NOTIFICATION);
	app->dispatcher = view_dispatcher_alloc();
	if (!app->lines || !app->timer || !app->gui
		|| !app->notification || !app->dispatcher)
		return (false);
	view_dispatcher_set_event_callback_context(app->dispatcher, app);
	view_dispatcher_set_custom_event_callback(app->dispatcher,
		flipnet_custom_event);
	view_dispatcher_set_navigation_event_callback(app->dispatcher,
		flipnet_navigation);
	return (true);
}

static bool	flipnet_app_views(t_flipnet_app *app)
{
	if (!flipnet_menus_alloc(app))
		return (false);
	app->overview = flipnet_overview_alloc(app);
	app->loading = flipnet_loading_alloc(app);
	app->feed_cards = flipnet_feed_alloc(app);
	app->input = text_input_alloc();
	app->result = flipnet_result_alloc(app);
	if (!app->overview || !app->loading || !app->feed_cards
		|| !app->input || !app->result)
		return (false);
	flipnet_views_add(app);
	app->views_added = true;
	view_dispatcher_attach_to_gui(app->dispatcher, app->gui,
		ViewDispatcherTypeFullscreen);
	return (true);
}

static void	flipnet_app_transports(t_flipnet_app *app)
{
	t_ble_cbs	ble;
	t_usb_cbs	usb;

	ble.line = flipnet_line_callback;
	ble.status = flipnet_ble_callback;
	ble.context = app;
	usb.line = flipnet_line_callback;
	usb.status = flipnet_usb_callback;
	usb.context = app;
	app->ble = flipnet_ble_alloc(ble);
	app->usb = flipnet_usb_alloc(usb);
	flipnet_transport_refresh(app);
}

t_flipnet_app	*flipnet_app_alloc(void)
{
	t_flipnet_app	*app;

	app = malloc(sizeof(t_flipnet_app));
	if (!app)
		return (NULL);
	memset(app, 0, sizeof(t_flipnet_app));
	flipnet_language_load(app);
	flipnet_capabilities_load(app);
	flipnet_history_load(app);
	flipnet_transport_load(app);
	app->session_id = (uint32_t)furi_get_tick();
	if (!app->session_id)
		app->session_id = 1U;
	app->current_view = VIEW_HOME;
	app->return_view = VIEW_HOME;
	app->feed_parent = VIEW_FEEDS;
	flipnet_copy(app->last_error, sizeof(app->last_error), "None");
	if (!flipnet_app_base(app) || !flipnet_app_views(app))
	{
		flipnet_app_free(app);
		return (NULL);
	}
	flipnet_app_transports(app);
	return (app);
}
