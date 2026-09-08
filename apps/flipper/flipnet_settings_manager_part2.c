#include "flipnet_settings_manager.h"

#include "flipnet_catalog.h"
#include "flipnet_core.h"
#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

#define FLIPNET_EXTENSION_CATALOG "FNEXT1\n"
#define FLIPNET_PLUGIN_CATALOG "FNPLUG1\n"
#define FLIPNET_PLUGIN_ACTION_TOGGLE 0U
#define FLIPNET_PLUGIN_ACTION_STATUS 1U
#define FLIPNET_PLUGIN_ACTION_REMOVE 2U
#define FLIPNET_PLUGIN_CONFIRM_CANCEL 0U
#define FLIPNET_PLUGIN_CONFIRM_REMOVE 1U

bool	flipnet_setting_state(t_flipnet_slice field);
const char	*flipnet_setting_prefix(char state);
const char	*flipnet_setting_reason_text(t_flipnet_app *app, char state);
void	flipnet_setting_label(char *output, size_t output_size,
			char state, t_flipnet_slice label);
bool	flipnet_setting_fields(t_flipnet_slice line,
			t_flipnet_slice *fields, uint8_t count);
void	flipnet_setting_reason(t_flipnet_app *app, const char *title,
			const char *reason, t_flipnet_view parent);
bool	flipnet_extension_item(t_flipnet_app *app, t_flipnet_slice line);
void	flipnet_extension_menu(t_flipnet_app *app);
bool	flipnet_plugin_item(t_flipnet_app *app, t_flipnet_slice line);
void	flipnet_plugin_menu(t_flipnet_app *app);
void	flipnet_plugin_actions_menu(t_flipnet_app *app);
void	flipnet_plugin_confirm_menu(t_flipnet_app *app);

void	flipnet_setting_reason(t_flipnet_app *app, const char *title,
			const char *reason, t_flipnet_view parent)
{
	app->return_view = parent;
	flipnet_result_set_kind(app, title, reason, CARD_WARNING);
	flipnet_switch(app, VIEW_RESULT);
}

bool	flipnet_extension_item(t_flipnet_app *app, t_flipnet_slice line)
{
	t_flipnet_slice	fields[4];
	uint8_t			index;
	uint8_t			label_index;

	if (app->extension_setting_count >= FLIPNET_EXTENSION_SETTING_ITEMS
		|| !flipnet_setting_fields(line, fields, 4U)
		|| !flipnet_slice_is_identifier(fields[0])
		|| !flipnet_setting_state(fields[1]))
		return (false);
	index = app->extension_setting_count;
	label_index = 2U;
	if (app->language == LANGUAGE_FR)
		label_index = 3U;
	if (!flipnet_slice_copy(app->extension_setting_ids[index],
			FLIPNET_SETTING_ID, fields[0]))
		return (false);
	app->extension_setting_states[index] = fields[1].data[0];
	flipnet_setting_label(app->extension_setting_labels[index],
		FLIPNET_SETTING_LABEL, fields[1].data[0], fields[label_index]);
	app->extension_setting_count++;
	return (true);
}

void	flipnet_extension_menu(t_flipnet_app *app)
{
	uint8_t	index;

	submenu_reset(app->extension_settings);
	submenu_set_header(app->extension_settings,
		flipnet_tr(app, "Extensions", "Extensions"));
	index = 0U;
	while (index < app->extension_setting_count)
	{
		submenu_add_item(app->extension_settings,
			app->extension_setting_labels[index], index,
			flipnet_extension_settings_callback, app);
		index++;
	}
	submenu_set_selected_item(app->extension_settings, 0U);
}

bool	flipnet_extension_settings_show(t_flipnet_app *app, const char *body)
{
	const char		*cursor;
	t_flipnet_slice	line;

	if (!body || strncmp(body, FLIPNET_EXTENSION_CATALOG,
			sizeof(FLIPNET_EXTENSION_CATALOG) - 1U))
		return (false);
	app->extension_setting_count = 0U;
	cursor = body + sizeof(FLIPNET_EXTENSION_CATALOG) - 1U;
	while (app->extension_setting_count < FLIPNET_EXTENSION_SETTING_ITEMS
		&& flipnet_catalog_next_line(&cursor, &line))
	{
		if (line.size)
			flipnet_extension_item(app, line);
	}
	if (!app->extension_setting_count)
		return (false);
	flipnet_extension_menu(app);
	flipnet_flow_stop(app);
	notification_message(app->notification, &sequence_success);
	flipnet_switch(app, VIEW_EXTENSION_SETTINGS);
	return (true);
}

void	flipnet_extension_settings_open(t_flipnet_app *app)
{
	if (!flipnet_has_service(app, FLIPNET_FEATURE_EXTENSION_MANAGER_LIST))
	{
		flipnet_setting_reason(app, "Extensions",
			flipnet_tr(app, "Desktop bridge required",
				"Passerelle desktop requise"), VIEW_SETTINGS);
		return ;
	}
	flipnet_request_direct(app, VIEW_SETTINGS,
		FLIPNET_FEATURE_EXTENSION_MANAGER_LIST, "");
}
