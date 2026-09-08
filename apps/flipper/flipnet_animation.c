#include "flipnet_core.h"
#include "flipnet_ui.h"

void	flipnet_animation_stop(t_flipnet_app *app)
{
	if (!app->animation)
		return ;
	app->animation = false;
	furi_timer_stop(app->timer);
}

static void	flipnet_wait_start(t_flipnet_app *app)
{
	app->animation_frame = 0U;
	app->animation = true;
	furi_timer_start(app->timer, furi_ms_to_ticks(FLIPNET_ANIMATION_MS));
}

void	flipnet_animation_start(t_flipnet_app *app)
{
	flipnet_loading_set(app, app->pending_service);
	flipnet_wait_start(app);
}

void	flipnet_background_start(t_flipnet_app *app)
{
	flipnet_wait_start(app);
}

void	flipnet_animation_callback(void *context)
{
	t_flipnet_app	*app;

	app = context;
	view_dispatcher_send_custom_event(app->dispatcher, EVENT_ANIMATION);
}
