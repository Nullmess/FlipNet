#include "flipnet_apps_i.h"
#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <stdio.h>
#include <string.h>

#define FLIPNET_APPS_CHOICES "FNSEL1\n"
#define FLIPNET_APP_MODE_SELECT 'S'
#define FLIPNET_APP_MODE_PROMPT 'P'

static void	flipnet_apps_choice_source(t_flipnet_app *app, uint8_t index)
{
	char	argument[FLIPNET_INPUT_SIZE];
	int		written;

	flipnet_copy(app->app_translation_source,
		sizeof(app->app_translation_source), app->app_choice_values[index]);
	flipnet_copy(app->app_choice_app_id, sizeof(app->app_choice_app_id),
		"translate_target");
	written = snprintf(argument, sizeof(argument), "translate_target|%s",
		app->app_translation_source);
	if (written > 0 && (size_t)written < sizeof(argument))
		flipnet_request_direct(app, VIEW_APPS, FLIPNET_FEATURE_APP_SEARCH,
			argument);
}

static void	flipnet_apps_choice_prompt(t_flipnet_app *app, uint8_t index)
{
	t_flipnet_app_input	input;
	t_flipnet_choice_output	output;
	char				history_key[FLIPNET_INPUT_SIZE];
	int				written;

	output.data = app->input_prefix;
	output.size = sizeof(app->input_prefix);
	output.trailing_space = true;
	if (!flipnet_apps_choice_argument(app, index, &output))
		return ;
	written = snprintf(history_key, sizeof(history_key), "app:%s",
		app->app_choice_app_id);
	if (written < 0 || (size_t)written >= sizeof(history_key))
		return ;
	input.service = FLIPNET_FEATURE_APP_RUN;
	input.history_key = history_key;
	input.initial = "";
	input.header = flipnet_tr(app, "Value", "Valeur");
	if (!strcmp(app->app_choice_app_id, "translate"))
		input.header = flipnet_tr(app, "Text to translate",
			"Texte a traduire");
	flipnet_apps_open_input(app, &input);
}

void	flipnet_apps_choice_callback(void *context, uint32_t index)
{
	t_flipnet_app			*app;
	t_flipnet_choice_output	output;
	char				argument[FLIPNET_INPUT_SIZE];

	app = context;
	if (index >= app->app_choice_count)
		return ;
	if (!strcmp(app->app_choice_app_id, "translate_source"))
		flipnet_apps_choice_source(app, (uint8_t)index);
	else if (!strcmp(app->app_choice_app_id, "translate_target"))
		flipnet_apps_translate_target(app, (uint8_t)index);
	else if (app->app_choice_mode == FLIPNET_APP_MODE_SELECT)
	{
		output.data = argument;
		output.size = sizeof(argument);
		output.trailing_space = false;
		if (flipnet_apps_choice_argument(app, (uint8_t)index, &output))
			flipnet_request_direct(app, VIEW_APPS,
				FLIPNET_FEATURE_APP_RUN, argument);
	}
	else if (app->app_choice_mode == FLIPNET_APP_MODE_PROMPT)
		flipnet_apps_choice_prompt(app, (uint8_t)index);
}

void	flipnet_apps_choice_menu(t_flipnet_app *app)
{
	const char	*header;
	uint8_t		index;

	header = flipnet_tr(app, "Choose timezone", "Choisir le fuseau");
	if (!strcmp(app->app_choice_app_id, "translate_source"))
		header = flipnet_tr(app, "Source language", "Langue d origine");
	else if (!strcmp(app->app_choice_app_id, "translate_target"))
		header = flipnet_tr(app, "Target language",
			"Langue de destination");
	else if (app->app_choice_mode == FLIPNET_APP_MODE_PROMPT)
		header = flipnet_tr(app, "Choose languages",
			"Choisir les langues");
	submenu_reset(app->app_choices);
	submenu_set_header(app->app_choices, header);
	index = 0U;
	while (index < app->app_choice_count)
	{
		submenu_add_item(app->app_choices,
			app->app_choice_labels[index], index,
			flipnet_apps_choice_callback, app);
		index++;
	}
	submenu_set_selected_item(app->app_choices, 0U);
}

bool	flipnet_apps_choices_show(t_flipnet_app *app, const char *body)
{
	const char		*cursor;
	t_flipnet_slice	app_id;
	t_flipnet_slice	line;
	if (!body || strncmp(body, FLIPNET_APPS_CHOICES,
			sizeof(FLIPNET_APPS_CHOICES) - 1U))
		return (false);
	cursor = body + sizeof(FLIPNET_APPS_CHOICES) - 1U;
	if (!flipnet_catalog_next_line(&cursor, &app_id)
		|| strlen(app->app_choice_app_id) != app_id.size
		|| strncmp(app_id.data, app->app_choice_app_id, app_id.size))
		return (false);
	app->app_choice_count = 0U;
	while (app->app_choice_count < FLIPNET_APP_CHOICE_ITEMS
		&& flipnet_catalog_next_line(&cursor, &line))
	{
		if (line.size)
			flipnet_apps_choice_item(app, line);
	}
	if (!app->app_choice_count)
		return (false);
	flipnet_apps_choice_menu(app);
	flipnet_flow_stop(app);
	notification_message(app->notification, &sequence_success);
	flipnet_switch(app, VIEW_APP_CHOICES);
	return (true);
}
