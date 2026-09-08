#include "flipnet_core.h"
#include "flipnet_ui.h"

void	flipnet_overview_draw(Canvas *canvas, void *model_pointer);
bool	flipnet_overview_input(InputEvent *event, void *context);

void	flipnet_overview_loading(t_flipnet_app *app)
{
	t_overview_model	*model;

	flipnet_transport_refresh(app);
	model = view_get_model(app->overview);
	model->transport = (uint8_t)flipnet_transport_choose(app);
	model->language = (uint8_t)app->language;
	model->loading = true;
	model->failed = false;
	view_commit_model(app->overview, true);
}

void	flipnet_overview_failed(t_flipnet_app *app)
{
	t_overview_model	*model;

	flipnet_transport_refresh(app);
	model = view_get_model(app->overview);
	model->transport = (uint8_t)flipnet_transport_choose(app);
	model->language = (uint8_t)app->language;
	model->loading = false;
	model->failed = true;
	view_commit_model(app->overview, true);
}

void	flipnet_overview_transport(t_flipnet_app *app)
{
	t_overview_model	*model;

	flipnet_transport_refresh(app);
	model = view_get_model(app->overview);
	model->transport = (uint8_t)flipnet_transport_choose(app);
	model->language = (uint8_t)app->language;
	view_commit_model(app->overview, true);
}

View	*flipnet_overview_alloc(t_flipnet_app *app)
{
	View	*view;

	view = view_alloc();
	view_allocate_model(view, ViewModelTypeLocking, sizeof(t_overview_model));
	view_set_context(view, app);
	view_set_draw_callback(view, flipnet_overview_draw);
	view_set_input_callback(view, flipnet_overview_input);
	return (view);
}
