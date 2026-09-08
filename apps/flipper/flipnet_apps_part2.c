#include "flipnet_apps_i.h"
#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <stdio.h>

void	flipnet_apps_menu(t_flipnet_app *app)
{
	uint8_t	index;

	submenu_reset(app->apps);
	submenu_set_header(app->apps,
		flipnet_tr(app, "Online apps", "Applications en ligne"));
	index = 0U;
	while (index < app->app_count)
	{
		submenu_add_item(app->apps, app->app_labels[index], index,
			flipnet_apps_callback, app);
		index++;
	}
	submenu_set_selected_item(app->apps, 0U);
}

bool	flipnet_apps_open_cached(t_flipnet_app *app)
{
	if (!app || !app->app_count)
		return (false);
	flipnet_apps_menu(app);
	flipnet_switch(app, VIEW_APPS);
	return (true);
}

bool	flipnet_apps_choice_item(t_flipnet_app *app, t_flipnet_slice line)
{
	t_flipnet_slice	value;
	t_flipnet_slice	label;
	uint8_t			index;

	if (app->app_choice_count >= FLIPNET_APP_CHOICE_ITEMS
		|| !flipnet_catalog_split_pair(line, &value, &label))
		return (false);
	index = app->app_choice_count;
	if (!flipnet_slice_copy_truncated(app->app_choice_values[index],
			FLIPNET_APP_CHOICE_VALUE, value)
		|| !flipnet_slice_copy_truncated(app->app_choice_labels[index],
			FLIPNET_APP_CHOICE_LABEL, label))
		return (false);
	app->app_choice_count++;
	return (true);
}

bool	flipnet_apps_choice_argument(t_flipnet_app *app, uint8_t index,
			t_flipnet_choice_output *output)
{
	const char	*suffix;
	int			written;

	suffix = "";
	if (output->trailing_space)
		suffix = " ";
	written = snprintf(output->data, output->size, "%s|%s%s",
		app->app_choice_app_id, app->app_choice_values[index], suffix);
	return (written > 0 && (size_t)written < output->size);
}

void	flipnet_apps_translate_target(t_flipnet_app *app, uint8_t index)
{
	t_flipnet_app_input	input;
	char				prefix[FLIPNET_INPUT_PREFIX_SIZE];
	int				written;

	written = snprintf(prefix, sizeof(prefix), "translate|%s>%s ",
		app->app_translation_source, app->app_choice_values[index]);
	if (written < 0 || (size_t)written >= sizeof(prefix))
		return ;
	flipnet_copy(app->input_prefix, sizeof(app->input_prefix), prefix);
	input.service = FLIPNET_FEATURE_APP_RUN;
	input.history_key = "app:translate";
	input.initial = "";
	input.header = flipnet_tr(app, "Text to translate", "Texte a traduire");
	flipnet_apps_open_input(app, &input);
}
