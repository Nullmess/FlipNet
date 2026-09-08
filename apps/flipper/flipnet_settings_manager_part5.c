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

void	flipnet_settings_management_changed(t_flipnet_app *app)
{
	flipnet_flow_stop(app);
	flipnet_switch(app, VIEW_SETTINGS);
	flipnet_capabilities_refresh(app);
}
