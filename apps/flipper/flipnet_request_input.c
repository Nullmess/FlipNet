#include "flipnet_core.h"

#include <string.h>

void	flipnet_request_begin(t_flipnet_app *app);

static void	flipnet_request_prepare_visible(t_flipnet_app *app)
{
	if (app->animation && app->request_silent)
		flipnet_cancel(app);
	app->request_silent = false;
}

void	flipnet_request_direct(t_flipnet_app *app, t_flipnet_view parent,
			const char *service, const char *argument)
{
	flipnet_request_prepare_visible(app);
	app->return_view = parent;
	flipnet_copy(app->pending_service,
		sizeof(app->pending_service), service);
	flipnet_copy(app->pending_argument,
		sizeof(app->pending_argument), argument);
	app->input_history_key[0] = '\0';
	flipnet_request_begin(app);
}

void	flipnet_request_direct_background(t_flipnet_app *app,
			t_flipnet_view parent, const char *service, const char *argument)
{
	if (!app || app->animation)
		return ;
	app->request_silent = true;
	app->return_view = parent;
	flipnet_copy(app->pending_service,
		sizeof(app->pending_service), service);
	flipnet_copy(app->pending_argument,
		sizeof(app->pending_argument), argument);
	app->input_history_key[0] = '\0';
	flipnet_request_begin(app);
}

void	flipnet_input_open(t_flipnet_app *app, t_input_spec spec)
{
	flipnet_request_prepare_visible(app);
	app->return_view = spec.parent;
	app->input_prefix[0] = '\0';
	flipnet_copy(app->input_history_key,
		sizeof(app->input_history_key), spec.service);
	flipnet_copy(app->pending_service, sizeof(app->pending_service),
		spec.service);
	{
		const char *saved;

		saved = flipnet_history_get(app, app->input_history_key);
		if (saved)
			flipnet_copy(app->input_buffer, sizeof(app->input_buffer), saved);
		else
			flipnet_copy(app->input_buffer, sizeof(app->input_buffer),
				spec.initial);
	}
	text_input_set_header_text(app->input, spec.prompt);
	text_input_set_result_callback(app->input, flipnet_input_done, app,
		app->input_buffer, sizeof(app->input_buffer), false);
	flipnet_switch(app, VIEW_INPUT);
}

void	flipnet_input_done(void *context)
{
	t_flipnet_app	*app;

	app = context;
	flipnet_request_prepare_visible(app);
	if (app->input_prefix[0])
	{
		size_t	prefix_size;
		size_t	input_size;

		prefix_size = strlen(app->input_prefix);
		input_size = strlen(app->input_buffer);
		if (prefix_size + input_size >= sizeof(app->pending_argument))
			return ;
		memcpy(app->pending_argument, app->input_prefix, prefix_size);
		memcpy(app->pending_argument + prefix_size, app->input_buffer,
			input_size + 1U);
	}
	else
		flipnet_copy(app->pending_argument, sizeof(app->pending_argument),
			app->input_buffer);
	flipnet_history_set(app, app->input_history_key, app->input_buffer);
	flipnet_request_begin(app);
}
