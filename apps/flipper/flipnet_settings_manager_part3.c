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

void	flipnet_extension_settings_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;
	char			state;

	app = context;
	if (index >= app->extension_setting_count)
		return ;
	state = app->extension_setting_states[index];
	if (state != 'E' && state != 'D' && state != 'P')
	{
		flipnet_setting_reason(app, app->extension_setting_labels[index],
			flipnet_setting_reason_text(app, state), VIEW_EXTENSION_SETTINGS);
		return ;
	}
	if (!flipnet_has_service(app, FLIPNET_FEATURE_EXTENSION_MANAGER_TOGGLE))
	{
		flipnet_setting_reason(app, app->extension_setting_labels[index],
			flipnet_tr(app, "Write permission required",
				"Permission d ecriture requise"), VIEW_EXTENSION_SETTINGS);
		return ;
	}
	flipnet_request_direct(app, VIEW_EXTENSION_SETTINGS,
		FLIPNET_FEATURE_EXTENSION_MANAGER_TOGGLE,
		app->extension_setting_ids[index]);
}

bool	flipnet_plugin_item(t_flipnet_app *app, t_flipnet_slice line)
{
	t_flipnet_slice	fields[5];
	uint8_t			index;
	uint8_t			label_index;

	if (app->plugin_setting_count >= FLIPNET_PLUGIN_SETTING_ITEMS
		|| !flipnet_setting_fields(line, fields, 5U)
		|| !flipnet_slice_is_identifier(fields[0])
		|| !flipnet_setting_state(fields[1]))
		return (false);
	index = app->plugin_setting_count;
	label_index = 3U;
	if (app->language == LANGUAGE_FR)
		label_index = 4U;
	if (!flipnet_slice_copy(app->plugin_setting_ids[index],
			FLIPNET_SETTING_ID, fields[0]))
		return (false);
	app->plugin_setting_states[index] = fields[1].data[0];
	flipnet_setting_label(app->plugin_setting_labels[index],
		FLIPNET_SETTING_LABEL, fields[1].data[0], fields[label_index]);
	app->plugin_setting_count++;
	return (true);
}

void	flipnet_plugin_menu(t_flipnet_app *app)
{
	uint8_t	index;

	submenu_reset(app->plugin_settings);
	submenu_set_header(app->plugin_settings,
		flipnet_tr(app, "Installed plugins", "Plugins installes"));
	if (!app->plugin_setting_count)
	{
		submenu_add_item(app->plugin_settings,
			flipnet_tr(app, "No installed plugin", "Aucun plugin installe"),
			UINT32_MAX, flipnet_plugin_settings_callback, app);
		return ;
	}
	index = 0U;
	while (index < app->plugin_setting_count)
	{
		submenu_add_item(app->plugin_settings,
			app->plugin_setting_labels[index], index,
			flipnet_plugin_settings_callback, app);
		index++;
	}
	submenu_set_selected_item(app->plugin_settings, 0U);
}

bool	flipnet_plugin_settings_show(t_flipnet_app *app, const char *body)
{
	const char		*cursor;
	t_flipnet_slice	line;
	size_t			header_size;

	header_size = sizeof(FLIPNET_PLUGIN_CATALOG) - 1U;
	if (!body || (strcmp(body, "FNPLUG1")
			&& strncmp(body, FLIPNET_PLUGIN_CATALOG, header_size)))
		return (false);
	app->plugin_setting_count = 0U;
	cursor = body + strlen(body);
	if (body[7] == '\n')
		cursor = body + header_size;
	while (app->plugin_setting_count < FLIPNET_PLUGIN_SETTING_ITEMS
		&& flipnet_catalog_next_line(&cursor, &line))
	{
		if (line.size)
			flipnet_plugin_item(app, line);
	}
	flipnet_plugin_menu(app);
	flipnet_flow_stop(app);
	notification_message(app->notification, &sequence_success);
	flipnet_switch(app, VIEW_PLUGIN_SETTINGS);
	return (true);
}

void	flipnet_plugin_settings_open(t_flipnet_app *app)
{
	if (!flipnet_has_service(app, FLIPNET_FEATURE_PLUGIN_MANAGER_LIST))
	{
		flipnet_setting_reason(app, "Plugins",
			flipnet_tr(app, "Desktop bridge required",
				"Passerelle desktop requise"), VIEW_SETTINGS);
		return ;
	}
	flipnet_request_direct(app, VIEW_SETTINGS,
		FLIPNET_FEATURE_PLUGIN_MANAGER_LIST, "");
}
