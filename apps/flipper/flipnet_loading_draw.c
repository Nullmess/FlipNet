#include "flipnet_app.h"

static const int32_t	g_wan_positions[] = {
	20, 25, 30, 35, 40, 45, 50, 78, 83, 88, 93,
	98, 93, 88, 83, 78, 50, 45, 40, 35, 30, 25
};
static const int32_t	g_local_positions[] = {
	20, 26, 32, 38, 44, 50, 56, 62, 68, 74, 80, 86,
	92, 98, 104, 98, 92, 86, 80, 74, 68, 62, 56, 50,
	44, 38, 32, 26
};
void	flipnet_loading_label(Canvas *canvas, int32_t x,
			const char *label);
void	flipnet_loading_packet(Canvas *canvas, int32_t x);
uint8_t	flipnet_loading_index(t_loading_model *model, uint8_t count);
void	flipnet_loading_wan(Canvas *canvas, t_loading_model *model);
void	flipnet_loading_local(Canvas *canvas, t_loading_model *model);
void	flipnet_loading_event(Canvas *canvas, t_loading_model *model);

void	flipnet_loading_label(Canvas *canvas, int32_t x,
			const char *label)
{
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str_aligned(canvas, x, 32, AlignCenter, AlignCenter, label);
}

void	flipnet_loading_packet(Canvas *canvas, int32_t x)
{
	canvas_draw_circle(canvas, x, 32, 3U);
	canvas_draw_disc(canvas, x, 32, 2U);
}

uint8_t	flipnet_loading_index(t_loading_model *model, uint8_t count)
{
	if (count == 0U)
		return (0U);
	if (model->determinate)
		return ((uint8_t)(((uint16_t)(count - 1U) * model->progress) / 100U));
	return (model->frame % count);
}

void	flipnet_loading_wan(Canvas *canvas, t_loading_model *model)
{
	uint8_t	index;

	index = flipnet_loading_index(model,
		(uint8_t)(sizeof(g_wan_positions) / sizeof(g_wan_positions[0])));
	canvas_draw_line(canvas, 20, 32, 50, 32);
	canvas_draw_line(canvas, 78, 32, 98, 32);
	flipnet_loading_packet(canvas, g_wan_positions[index]);
	flipnet_loading_label(canvas, 8, "FZ");
	flipnet_loading_label(canvas, 64, "GW");
	flipnet_loading_label(canvas, 114, "WAN");
}

void	flipnet_loading_local(Canvas *canvas, t_loading_model *model)
{
	uint8_t	index;

	index = flipnet_loading_index(model,
		(uint8_t)(sizeof(g_local_positions) / sizeof(g_local_positions[0])));
	canvas_draw_line(canvas, 20, 32, 104, 32);
	flipnet_loading_packet(canvas, g_local_positions[index]);
	flipnet_loading_label(canvas, 8, "FZ");
	flipnet_loading_label(canvas, 116, "GW");
}
