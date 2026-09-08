#include "flipnet_features_generated.h"
#include "flipnet_apps_i.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <stdio.h>
#include <string.h>

#define FLIPNET_APP_MODE_RUN 'R'
#define FLIPNET_APP_MODE_SELECT 'S'
#define FLIPNET_APP_MODE_PROMPT 'P'
#define FLIPNET_APP_MODE_TRANSLATE 'T'

bool	flipnet_apps_prefix(t_flipnet_app *app, const char *app_id)
{
	size_t	id_size;

	id_size = strlen(app_id);
	if (id_size + 1U >= sizeof(app->input_prefix))
		return (false);
	memcpy(app->input_prefix, app_id, id_size);
	app->input_prefix[id_size] = '|';
	app->input_prefix[id_size + 1U] = '\0';
	return (true);
}

void	flipnet_apps_open_input(t_flipnet_app *app,
			const t_flipnet_app_input *input)
{
	const char	*value;

	app->return_view = VIEW_APPS;
	flipnet_copy(app->pending_service, sizeof(app->pending_service),
		input->service);
	flipnet_copy(app->input_history_key, sizeof(app->input_history_key),
		input->history_key);
	value = flipnet_history_get(app, app->input_history_key);
	if (!value)
		value = input->initial;
	flipnet_copy(app->input_buffer, sizeof(app->input_buffer), value);
	text_input_set_header_text(app->input, input->header);
	text_input_set_result_callback(app->input, flipnet_input_done, app,
		app->input_buffer, sizeof(app->input_buffer), false);
	flipnet_switch(app, VIEW_INPUT);
}

bool	flipnet_apps_input(t_flipnet_app *app, uint8_t index,
			const char *service)
{
	t_flipnet_app_input	input;
	char				history_key[FLIPNET_HISTORY_KEY];
	int				written;

	written = snprintf(history_key, sizeof(history_key), "app:%s",
		app->app_ids[index]);
	if (written < 0 || (size_t)written >= sizeof(history_key))
		return (false);
	input.service = service;
	input.history_key = history_key;
	input.initial = app->app_initials[index];
	input.header = app->app_prompts[index];
	if (!input.header[0])
		input.header = flipnet_tr(app, "Value", "Valeur");
	flipnet_apps_open_input(app, &input);
	return (true);
}

static void	flipnet_apps_run_selected(t_flipnet_app *app, uint8_t index,
			char mode)
{
	char	argument[FLIPNET_APP_ID + 2U];

	if (mode == FLIPNET_APP_MODE_TRANSLATE)
	{
		flipnet_copy(app->app_choice_app_id,
			sizeof(app->app_choice_app_id), "translate_source");
		flipnet_request_direct(app, VIEW_APPS, FLIPNET_FEATURE_APP_SEARCH,
			"translate_source|");
	}
	else if (mode == FLIPNET_APP_MODE_RUN)
	{
		flipnet_copy(argument, sizeof(argument), app->input_prefix);
		flipnet_request_direct(app, VIEW_APPS, FLIPNET_FEATURE_APP_RUN,
			argument);
	}
	else if (mode == FLIPNET_APP_MODE_SELECT
		|| mode == FLIPNET_APP_MODE_PROMPT)
		flipnet_apps_input(app, index, FLIPNET_FEATURE_APP_SEARCH);
	else
		flipnet_apps_input(app, index, FLIPNET_FEATURE_APP_RUN);
}

void	flipnet_apps_callback(void *context, uint32_t index)
{
	t_flipnet_app	*app;
	char			mode;

	app = context;
	if (index >= app->app_count
		|| !flipnet_apps_prefix(app, app->app_ids[index]))
		return ;
	mode = app->app_modes[index];
	app->app_choice_mode = mode;
	flipnet_copy(app->app_choice_app_id, sizeof(app->app_choice_app_id),
		app->app_ids[index]);
	flipnet_apps_run_selected(app, (uint8_t)index, mode);
}
