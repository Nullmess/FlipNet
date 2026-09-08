#include "flipnet_features_generated.h"
#include "flipnet_catalog.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

#define FLIPNET_WIKIPEDIA_CATALOG "FNWIKI1\n"

static bool	flipnet_wikipedia_item(t_flipnet_app *app,
			t_flipnet_slice line)
{
	t_flipnet_slice	id;
	t_flipnet_slice	label;
	uint8_t			index;

	if (app->wiki_count >= FLIPNET_WIKI_ITEMS
		|| !flipnet_catalog_split_pair(line, &id, &label)
		|| id.size >= FLIPNET_WIKI_ID
		|| !flipnet_slice_is_decimal(id))
		return (false);
	index = app->wiki_count;
	if (!flipnet_slice_copy(app->wiki_ids[index], FLIPNET_WIKI_ID, id)
		|| !flipnet_slice_copy_truncated(app->wiki_labels[index],
			FLIPNET_WIKI_LABEL, label))
		return (false);
	app->wiki_count++;
	return (true);
}

void	flipnet_wikipedia_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (index >= app->wiki_count)
		return ;
	flipnet_request_direct(app, VIEW_WIKI_RESULTS, FLIPNET_FEATURE_WIKI_PAGE,
		app->wiki_ids[index]);
}

static void	flipnet_wikipedia_menu(t_flipnet_app *app)
{
	uint8_t	index;

	submenu_reset(app->wiki_results);
	submenu_set_header(app->wiki_results,
		flipnet_tr(app, "Choose an article", "Choisir un article"));
	index = 0U;
	while (index < app->wiki_count)
	{
		submenu_add_item(app->wiki_results, app->wiki_labels[index], index,
			flipnet_wikipedia_callback, app);
		index++;
	}
	submenu_set_selected_item(app->wiki_results, 0U);
}

bool	flipnet_wikipedia_show(t_flipnet_app *app, const char *body)
{
	const char		*cursor;
	t_flipnet_slice	line;

	if (!body || strncmp(body, FLIPNET_WIKIPEDIA_CATALOG,
			sizeof(FLIPNET_WIKIPEDIA_CATALOG) - 1U))
		return (false);
	app->wiki_count = 0U;
	cursor = body + sizeof(FLIPNET_WIKIPEDIA_CATALOG) - 1U;
	while (app->wiki_count < FLIPNET_WIKI_ITEMS
		&& flipnet_catalog_next_line(&cursor, &line))
	{
		if (line.size)
			flipnet_wikipedia_item(app, line);
	}
	if (!app->wiki_count)
		return (false);
	flipnet_wikipedia_menu(app);
	flipnet_flow_stop(app);
	notification_message(app->notification, &sequence_success);
	flipnet_switch(app, VIEW_WIKI_RESULTS);
	return (true);
}
