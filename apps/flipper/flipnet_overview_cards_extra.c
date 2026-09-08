#include "flipnet_app.h"

#include <stdio.h>

static const char	*flipnet_overview_extra_label(t_overview_model *model,
			const char *english, const char *french)
{
	if (model->language == LANGUAGE_FR)
		return (french);
	return (english);
}

void	flipnet_overview_workspace(Canvas *canvas, t_overview_model *model)
{
	char	value[40];

	canvas_draw_rframe(canvas, 14, 23, 22, 25, 3U);
	canvas_draw_line(canvas, 18, 29, 32, 29);
	canvas_draw_line(canvas, 18, 35, 32, 35);
	snprintf(value, sizeof(value), "T:%u F:%u H:%u",
		model->targets, model->favorites, model->history);
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str(canvas, 48, 31,
		flipnet_overview_extra_label(model, "WORKSPACE", "ESPACE"));
	canvas_set_font(canvas, FontSecondary);
	canvas_draw_str(canvas, 48, 43, value);
}

void	flipnet_overview_devices(Canvas *canvas, t_overview_model *model)
{
	char	value[40];

	canvas_draw_rframe(canvas, 12, 22, 28, 27, 3U);
	canvas_draw_line(canvas, 17, 29, 35, 29);
	canvas_draw_line(canvas, 17, 36, 35, 36);
	snprintf(value, sizeof(value), "%u  +%u -%u ~%u", model->devices,
		model->new_devices, model->missing_devices,
		model->changed_devices);
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str(canvas, 48, 31,
		flipnet_overview_extra_label(model, "DEVICES", "APPAREILS"));
	canvas_set_font(canvas, FontSecondary);
	canvas_draw_str(canvas, 48, 43, value);
}
