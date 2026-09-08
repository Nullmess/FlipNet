#include "flipnet_app.h"
#include "flipnet_core.h"
#include "flipnet_ui.h"

static void	flipnet_app_open(t_flipnet_app *app, t_flipnet_view resume_view,
			bool resume_valid)
{
	t_flipnet_view	view;

	if (app->language == LANGUAGE_UNSET)
	{
		flipnet_switch(app, VIEW_LANGUAGE);
		return ;
	}
	if (app->capabilities->loaded
		&& app->catalog_transport != TRANSPORT_NONE
		&& flipnet_transport_choose(app) != TRANSPORT_NONE
		&& flipnet_transport_choose(app) != app->catalog_transport)
		flipnet_capabilities_invalidate(app);
	view = VIEW_HOME;
	if (resume_valid)
		view = resume_view;
	flipnet_switch(app, view);
	if (flipnet_capabilities_resume_apps())
		flipnet_apps_open_cached(app);
	else if (!app->capabilities->loaded && !app->capabilities_attempted
		&& flipnet_transport_choose(app) != TRANSPORT_NONE)
		flipnet_capabilities_refresh_background(app);
}

static bool	flipnet_app_run(t_flipnet_view *resume_view,
			bool *resume_valid)
{
	t_flipnet_app	*app;
	bool			restart;

	app = flipnet_app_alloc();
	if (!app)
		return (false);
	flipnet_app_open(app, *resume_view, *resume_valid);
	*resume_valid = false;
	view_dispatcher_run(app->dispatcher);
	restart = app->restart_requested;
	if (restart)
	{
		*resume_view = app->current_view;
		if (*resume_view == VIEW_LOADING)
			*resume_view = app->return_view;
		*resume_valid = true;
	}
	flipnet_app_free(app);
	return (restart);
}

int32_t	flipnet_app(void *context)
{
	t_flipnet_view	resume_view;
	bool			restart;
	bool			resume_valid;

	UNUSED(context);
	restart = true;
	resume_view = VIEW_HOME;
	resume_valid = false;
	while (restart)
		restart = flipnet_app_run(&resume_view, &resume_valid);
	return (0);
}
