#include "flipnet_app.h"

#include <stdio.h>

static const char	*flipnet_overview_label(t_overview_model *model,
			const char *english, const char *french)
{
	if (model->language == LANGUAGE_FR)
		return (french);
	return (english);
}

void	flipnet_overview_dots(Canvas *canvas, uint8_t page)
{
	uint8_t	index;
	int32_t	x;

	index = 0U;
	while (index < 5U)
	{
		x = 100 + index * 8;
		if (index == page)
			canvas_draw_disc(canvas, x, 6, 2U);
		else
			canvas_draw_circle(canvas, x, 6, 2U);
		index++;
	}
}

void	flipnet_overview_bridge(Canvas *canvas, t_overview_model *model)
{
	const char	*label;

	canvas_draw_circle(canvas, 25, 35, 13U);
	canvas_draw_disc(canvas, 20, 35, 2U);
	canvas_draw_disc(canvas, 30, 35, 2U);
	canvas_draw_line(canvas, 22, 35, 28, 35);
	canvas_set_font(canvas, FontPrimary);
	label = flipnet_overview_label(model, "LINK", "LIEN");
	if (model->transport == TRANSPORT_USB)
		label = "USB";
	else if (model->transport == TRANSPORT_BLE)
		label = "BLE";
	canvas_draw_str(canvas, 48, 31, label);
	canvas_set_font(canvas, FontSecondary);
	label = flipnet_overview_label(model, "ONLINE", "EN LIGNE");
	if (model->transport == TRANSPORT_NONE)
		label = flipnet_overview_label(model, "OFFLINE", "HORS LIGNE");
	else if (model->loading)
		label = flipnet_overview_label(model, "REFRESHING", "ACTUALISATION");
	canvas_draw_str(canvas, 48, 43, label);
}

void	flipnet_overview_monitors(Canvas *canvas, t_overview_model *model)
{
	char	value[24];

	canvas_draw_circle(canvas, 25, 35, 13U);
	canvas_draw_line(canvas, 19, 35, 23, 40);
	canvas_draw_line(canvas, 23, 40, 32, 29);
	snprintf(value, sizeof(value), "%u/%u OK", model->monitor_ok,
		model->monitors);
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str(canvas, 48, 31, "MONITORS");
	canvas_set_font(canvas, FontSecondary);
	canvas_draw_str(canvas, 48, 43, value);
}

void	flipnet_overview_alerts(Canvas *canvas, t_overview_model *model)
{
	char		value[24];
	const char	*format;

	canvas_draw_triangle(canvas, 25, 22, 13, 25, CanvasDirectionTopToBottom);
	canvas_draw_disc(canvas, 25, 34, 1U);
	canvas_draw_disc(canvas, 25, 40, 1U);
	format = "%u ACTIVE";
	if (model->language == LANGUAGE_FR)
		format = "%u ACTIVES";
	snprintf(value, sizeof(value), format, model->alerts);
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str(canvas, 48, 31,
		flipnet_overview_label(model, "ALERTS", "ALERTES"));
	canvas_set_font(canvas, FontSecondary);
	canvas_draw_str(canvas, 48, 43, value);
}
