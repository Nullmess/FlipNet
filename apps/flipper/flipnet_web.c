#include "flipnet_features_generated.h"
#include "flipnet_catalog.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

#define FLIPNET_WEB_CATALOG "FNWEB1\n"

static bool	flipnet_web_item(t_flipnet_app *app, t_flipnet_slice line)
{
	t_flipnet_slice	id;
	t_flipnet_slice	label;
	uint8_t			index;

	if (app->web_count >= FLIPNET_WEB_ITEMS
		|| !flipnet_catalog_split_pair(line, &id, &label)
		|| id.size < 8U || id.size >= FLIPNET_WEB_ID
		|| !flipnet_slice_is_upper_hex(id))
		return (false);
	index = app->web_count;
	if (!flipnet_slice_copy(app->web_ids[index], FLIPNET_WEB_ID, id)
		|| !flipnet_slice_copy_truncated(app->web_labels[index],
			FLIPNET_WEB_LABEL, label))
		return (false);
	app->web_count++;
	return (true);
}

void	flipnet_web_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (index >= app->web_count)
		return ;
	flipnet_request_direct(app, VIEW_WEB_RESULTS, FLIPNET_FEATURE_WEB_PAGE,
		app->web_ids[index]);
}

static void	flipnet_web_menu(t_flipnet_app *app)
{
	uint8_t	index;

	submenu_reset(app->web_results);
	submenu_set_header(app->web_results,
		flipnet_tr(app, "Choose a result", "Choisir un resultat"));
	index = 0U;
	while (index < app->web_count)
	{
		submenu_add_item(app->web_results, app->web_labels[index], index,
			flipnet_web_callback, app);
		index++;
	}
	submenu_set_selected_item(app->web_results, 0U);
}

bool	flipnet_web_show(t_flipnet_app *app, const char *body)
{
	const char		*cursor;
	t_flipnet_slice	line;

	if (!body || strncmp(body, FLIPNET_WEB_CATALOG,
			sizeof(FLIPNET_WEB_CATALOG) - 1U))
		return (false);
	app->web_count = 0U;
	cursor = body + sizeof(FLIPNET_WEB_CATALOG) - 1U;
	while (app->web_count < FLIPNET_WEB_ITEMS
		&& flipnet_catalog_next_line(&cursor, &line))
	{
		if (line.size)
			flipnet_web_item(app, line);
	}
	if (!app->web_count)
		return (false);
	flipnet_web_menu(app);
	flipnet_flow_stop(app);
	notification_message(app->notification, &sequence_success);
	flipnet_switch(app, VIEW_WEB_RESULTS);
	return (true);
}
