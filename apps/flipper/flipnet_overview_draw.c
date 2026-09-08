#include "flipnet_app.h"
#include "flipnet_ui.h"

void	flipnet_overview_dots(Canvas *canvas, uint8_t page);
void	flipnet_overview_bridge(Canvas *canvas, t_overview_model *model);
void	flipnet_overview_monitors(Canvas *canvas, t_overview_model *model);
void	flipnet_overview_alerts(Canvas *canvas, t_overview_model *model);
void	flipnet_overview_workspace(Canvas *canvas, t_overview_model *model);
void	flipnet_overview_devices(Canvas *canvas, t_overview_model *model);

void	flipnet_overview_draw(Canvas *canvas, void *model_pointer)
{
	t_overview_model	*model;

	model = model_pointer;
	canvas_clear(canvas);
	if (model->language == LANGUAGE_FR)
		flipnet_status_bar(canvas, "VUE GENERALE", TRANSPORT_NONE);
	else
		flipnet_status_bar(canvas, "OVERVIEW", TRANSPORT_NONE);
	flipnet_overview_dots(canvas, model->page);
	if (model->page == 0U)
		flipnet_overview_bridge(canvas, model);
	else if (model->page == 1U)
		flipnet_overview_monitors(canvas, model);
	else if (model->page == 2U)
		flipnet_overview_devices(canvas, model);
	else if (model->page == 3U)
		flipnet_overview_alerts(canvas, model);
	else
		flipnet_overview_workspace(canvas, model);
	if (model->language == LANGUAGE_FR)
		canvas_draw_str_aligned(canvas, 64, 62, AlignCenter, AlignCenter,
			"<  CARTE  >");
	else
		canvas_draw_str_aligned(canvas, 64, 62, AlignCenter, AlignCenter,
			"<  CARD  >");
}

bool	flipnet_overview_input(InputEvent *event, void *context)
{
	t_flipnet_app		*app;
	t_overview_model	*model;
	bool				changed;

	app = context;
	if (event->type != InputTypeShort && event->type != InputTypeRepeat)
		return (false);
	model = view_get_model(app->overview);
	changed = false;
	if (event->key == InputKeyLeft && model->page > 0U)
	{
		model->page--;
		changed = true;
	}
	if (event->key == InputKeyRight && model->page < 4U)
	{
		model->page++;
		changed = true;
	}
	view_commit_model(app->overview, changed);
	return (changed);
}
