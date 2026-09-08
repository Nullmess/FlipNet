#include "flipnet_app.h"
#include "flipnet_ui.h"

void			flipnet_loading_draw(Canvas *canvas, void *model_pointer);
t_flipnet_route	flipnet_route(const char *service);

void	flipnet_loading_set(t_flipnet_app *app, const char *service)
{
	t_loading_model	*model;

	model = view_get_model(app->loading);
	model->route = (uint8_t)flipnet_route(service);
	model->frame = app->animation_frame;
	model->transport = app->pending_transport;
	model->progress = 0U;
	model->determinate = false;
	view_commit_model(app->loading, true);
}

void	flipnet_loading_tick(t_flipnet_app *app)
{
	t_loading_model	*model;

	model = view_get_model(app->loading);
	model->frame = app->animation_frame;
	model->transport = app->pending_transport;
	view_commit_model(app->loading, true);
}

void	flipnet_loading_progress(t_flipnet_app *app, uint8_t progress)
{
	t_loading_model	*model;

	model = view_get_model(app->loading);
	if (progress > 100U)
		progress = 100U;
	model->progress = progress;
	model->determinate = true;
	view_commit_model(app->loading, true);
}

View	*flipnet_loading_alloc(t_flipnet_app *app)
{
	View	*view;

	view = view_alloc();
	view_allocate_model(view, ViewModelTypeLocking, sizeof(t_loading_model));
	view_set_context(view, app);
	view_set_draw_callback(view, flipnet_loading_draw);
	return (view);
}
