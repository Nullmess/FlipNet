#include "flipnet_app.h"

static const int32_t	g_event_positions[] = {
	104, 98, 92, 86, 80, 74, 68, 62, 56, 50, 44, 38,
	32, 26, 20
};

void	flipnet_loading_label(Canvas *canvas, int32_t x,
			const char *label);
void	flipnet_loading_packet(Canvas *canvas, int32_t x);
uint8_t	flipnet_loading_index(t_loading_model *model, uint8_t count);
void	flipnet_loading_wan(Canvas *canvas, t_loading_model *model);
void	flipnet_loading_local(Canvas *canvas, t_loading_model *model);
void	flipnet_loading_event(Canvas *canvas, t_loading_model *model);

void	flipnet_loading_event(Canvas *canvas, t_loading_model *model)
{
	uint8_t	index;

	index = flipnet_loading_index(model,
		(uint8_t)(sizeof(g_event_positions) / sizeof(g_event_positions[0])));
	canvas_draw_line(canvas, 20, 32, 104, 32);
	flipnet_loading_packet(canvas, g_event_positions[index]);
	flipnet_loading_label(canvas, 8, "FZ");
	flipnet_loading_label(canvas, 116, "GW");
}

void	flipnet_loading_draw(Canvas *canvas, void *model_pointer)
{
	t_loading_model	*model;

	model = model_pointer;
	canvas_clear(canvas);
	if (model->route == ROUTE_WAN)
		flipnet_loading_wan(canvas, model);
	else if (model->route == ROUTE_EVENT)
		flipnet_loading_event(canvas, model);
	else
		flipnet_loading_local(canvas, model);
}
