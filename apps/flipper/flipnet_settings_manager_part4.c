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

void	flipnet_plugin_actions_menu(t_flipnet_app *app)
{
	submenu_reset(app->plugin_actions);
	submenu_set_header(app->plugin_actions, app->selected_plugin_label);
	if ((app->selected_plugin_state == 'E' || app->selected_plugin_state == 'D')
		&& flipnet_has_service(app, FLIPNET_FEATURE_PLUGIN_MANAGER_TOGGLE))
	{
		if (app->selected_plugin_state == 'E')
			submenu_add_item(app->plugin_actions,
				flipnet_tr(app, "Disable", "Desactiver"),
				FLIPNET_PLUGIN_ACTION_TOGGLE,
				flipnet_plugin_action_callback, app);
		else
			submenu_add_item(app->plugin_actions,
				flipnet_tr(app, "Enable", "Activer"),
				FLIPNET_PLUGIN_ACTION_TOGGLE,
				flipnet_plugin_action_callback, app);
	}
	submenu_add_item(app->plugin_actions,
		flipnet_tr(app, "Status", "Etat"), FLIPNET_PLUGIN_ACTION_STATUS,
		flipnet_plugin_action_callback, app);
	if (flipnet_has_service(app, FLIPNET_FEATURE_PLUGIN_MANAGER_REMOVE))
		submenu_add_item(app->plugin_actions,
			flipnet_tr(app, "Remove", "Supprimer"),
			FLIPNET_PLUGIN_ACTION_REMOVE, flipnet_plugin_action_callback, app);
	submenu_set_selected_item(app->plugin_actions, 0U);
}

void	flipnet_plugin_settings_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (index >= app->plugin_setting_count)
		return ;
	flipnet_copy(app->selected_plugin_id, sizeof(app->selected_plugin_id),
		app->plugin_setting_ids[index]);
	flipnet_copy(app->selected_plugin_label, sizeof(app->selected_plugin_label),
		app->plugin_setting_labels[index]);
	app->selected_plugin_state = app->plugin_setting_states[index];
	flipnet_plugin_actions_menu(app);
	flipnet_switch(app, VIEW_PLUGIN_ACTIONS);
}

void	flipnet_plugin_confirm_menu(t_flipnet_app *app)
{
	submenu_reset(app->plugin_confirm);
	submenu_set_header(app->plugin_confirm,
		flipnet_tr(app, "Remove plugin", "Supprimer le plugin"));
	submenu_add_item(app->plugin_confirm,
		flipnet_tr(app, "Cancel", "Annuler"),
		FLIPNET_PLUGIN_CONFIRM_CANCEL, flipnet_plugin_confirm_callback, app);
	submenu_add_item(app->plugin_confirm,
		flipnet_tr(app, "Confirm removal", "Confirmer la suppression"),
		FLIPNET_PLUGIN_CONFIRM_REMOVE, flipnet_plugin_confirm_callback, app);
	submenu_set_selected_item(app->plugin_confirm, 0U);
}

void	flipnet_plugin_action_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (index == FLIPNET_PLUGIN_ACTION_TOGGLE)
		flipnet_request_direct(app, VIEW_PLUGIN_ACTIONS,
			FLIPNET_FEATURE_PLUGIN_MANAGER_TOGGLE, app->selected_plugin_id);
	else if (index == FLIPNET_PLUGIN_ACTION_STATUS)
		flipnet_setting_reason(app, app->selected_plugin_label,
			flipnet_setting_reason_text(app, app->selected_plugin_state),
			VIEW_PLUGIN_ACTIONS);
	else if (index == FLIPNET_PLUGIN_ACTION_REMOVE)
	{
		flipnet_plugin_confirm_menu(app);
		flipnet_switch(app, VIEW_PLUGIN_CONFIRM);
	}
}

void	flipnet_plugin_confirm_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;

	app = context;
	if (index == FLIPNET_PLUGIN_CONFIRM_CANCEL)
		flipnet_switch(app, VIEW_PLUGIN_ACTIONS);
	else if (index == FLIPNET_PLUGIN_CONFIRM_REMOVE)
		flipnet_request_direct(app, VIEW_PLUGIN_CONFIRM,
			FLIPNET_FEATURE_PLUGIN_MANAGER_REMOVE, app->selected_plugin_id);
}
