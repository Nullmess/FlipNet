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

bool	flipnet_setting_state(t_flipnet_slice field)
{
	if (field.size != 1U)
		return (false);
	return (strchr("EDPUB", field.data[0]) != NULL);
}

const char	*flipnet_setting_prefix(char state)
{
	if (state == 'E')
		return ("[ON]");
	if (state == 'D')
		return ("[OFF]");
	if (state == 'P')
		return ("[LOCK]");
	if (state == 'B')
		return ("[ERR]");
	return ("[N/A]");
}

const char	*flipnet_setting_reason_text(t_flipnet_app *app, char state)
{
	if (state == 'E')
		return (flipnet_tr(app, "Enabled", "Active"));
	if (state == 'D')
		return (flipnet_tr(app, "Disabled", "Desactive"));
	if (state == 'P')
		return (flipnet_tr(app, "Permission denied", "Permission refusee"));
	if (state == 'B')
		return (flipnet_tr(app, "Plugin needs attention",
				"Le plugin demande une verification"));
	return (flipnet_tr(app, "Unavailable", "Indisponible"));
}

void	flipnet_setting_label(char *output, size_t output_size,
			char state, t_flipnet_slice label)
{
	const char	*prefix;
	size_t		prefix_size;
	size_t		label_size;

	if (!output || output_size == 0U)
		return ;
	prefix = flipnet_setting_prefix(state);
	prefix_size = strlen(prefix);
	if (prefix_size >= output_size)
	{
		flipnet_copy(output, output_size, prefix);
		return ;
	}
	memcpy(output, prefix, prefix_size);
	output[prefix_size] = '\0';
	if (!label.data || prefix_size + 2U > output_size)
		return ;
	output[prefix_size] = ' ';
	label_size = label.size;
	if (label_size > output_size - prefix_size - 2U)
		label_size = output_size - prefix_size - 2U;
	memcpy(output + prefix_size + 1U, label.data, label_size);
	output[prefix_size + label_size + 1U] = '\0';
}

bool	flipnet_setting_fields(t_flipnet_slice line,
			t_flipnet_slice *fields, uint8_t count)
{
	const char	*cursor;
	const char	*end;
	uint8_t		index;

	cursor = line.data;
	end = line.data + line.size;
	index = 0U;
	while (index < count)
	{
		if (!flipnet_catalog_next_field(&cursor, end, &fields[index]))
			return (false);
		index++;
	}
	return (cursor == end);
}
