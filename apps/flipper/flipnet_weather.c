#include "flipnet_features_generated.h"
#include "flipnet_catalog.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

#define FLIPNET_WEATHER_CATALOG "FNLOC1\n"

static bool	flipnet_weather_item(t_flipnet_app *app, t_flipnet_slice line)
{
	t_flipnet_slice	id;
	t_flipnet_slice	label;
	uint8_t			index;

	if (app->weather_count >= FLIPNET_WEATHER_ITEMS
		|| !flipnet_catalog_split_pair(line, &id, &label)
		|| id.size >= FLIPNET_WEATHER_ID
		|| !flipnet_slice_is_decimal(id))
		return (false);
	index = app->weather_count;
	if (!flipnet_slice_copy(app->weather_ids[index], FLIPNET_WEATHER_ID, id)
		|| !flipnet_slice_copy_truncated(app->weather_labels[index],
			FLIPNET_WEATHER_LABEL, label))
		return (false);
	app->weather_count++;
	return (true);
}

void	flipnet_weather_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (index >= app->weather_count)
		return ;
	flipnet_request_direct(app, VIEW_WEATHER_RESULTS,
		FLIPNET_FEATURE_WEATHER_LOCATION, app->weather_ids[index]);
}

static void	flipnet_weather_menu(t_flipnet_app *app)
{
	uint8_t	index;

	submenu_reset(app->weather_results);
	submenu_set_header(app->weather_results,
		flipnet_tr(app, "Choose a city", "Choisir une ville"));
	index = 0U;
	while (index < app->weather_count)
	{
		submenu_add_item(app->weather_results, app->weather_labels[index],
			index, flipnet_weather_callback, app);
		index++;
	}
	submenu_set_selected_item(app->weather_results, 0U);
}

bool	flipnet_weather_show(t_flipnet_app *app, const char *body)
{
	const char		*cursor;
	t_flipnet_slice	line;

	if (!body || strncmp(body, FLIPNET_WEATHER_CATALOG,
			sizeof(FLIPNET_WEATHER_CATALOG) - 1U))
		return (false);
	app->weather_count = 0U;
	cursor = body + sizeof(FLIPNET_WEATHER_CATALOG) - 1U;
	while (app->weather_count < FLIPNET_WEATHER_ITEMS
		&& flipnet_catalog_next_line(&cursor, &line))
	{
		if (line.size)
			flipnet_weather_item(app, line);
	}
	if (!app->weather_count)
		return (false);
	flipnet_weather_menu(app);
	flipnet_flow_stop(app);
	notification_message(app->notification, &sequence_success);
	flipnet_switch(app, VIEW_WEATHER_RESULTS);
	return (true);
}
