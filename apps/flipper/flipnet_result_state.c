#include "flipnet_core.h"
#include "flipnet_ui.h"

#include <string.h>

void		flipnet_result_draw(Canvas *canvas, void *model_pointer);
bool		flipnet_result_input(InputEvent *event, void *context);

void	flipnet_result_render(t_flipnet_app *app)
{
	t_result_model	*model;

	model = view_get_model(app->result);
	model->page = 0U;
	model->scroll = 0U;
	model->pages = 1U;
	model->scroll_max = 0U;
	memset(model->page_lines, 0, sizeof(model->page_lines));
	model->title = app->result_title;
	model->body = app->result_body;
	view_commit_model(app->result, true);
}

void	flipnet_result_set_kind(t_flipnet_app *app, const char *title,
			const char *body, t_flipnet_card kind)
{
	t_result_model	*model;

	flipnet_copy(app->result_title, sizeof(app->result_title), title);
	flipnet_copy(app->result_body, sizeof(app->result_body), body);
	model = view_get_model(app->result);
	model->kind = (uint8_t)kind;
	view_commit_model(app->result, false);
	flipnet_result_render(app);
}

void	flipnet_result_set(t_flipnet_app *app, const char *title,
			const char *body)
{
	flipnet_result_set_kind(app, title, body, CARD_SUCCESS);
}

View	*flipnet_result_alloc(t_flipnet_app *app)
{
	View	*view;

	view = view_alloc();
	view_allocate_model(view, ViewModelTypeLocking, sizeof(t_result_model));
	view_set_context(view, app);
	view_set_draw_callback(view, flipnet_result_draw);
	view_set_input_callback(view, flipnet_result_input);
	return (view);
}
