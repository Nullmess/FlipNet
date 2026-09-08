#include "flipnet_capabilities_i.h"

#include <string.h>

t_flipnet_capability_state	*flipnet_capabilities_state(void)
{
	static t_flipnet_capability_state	state;

	return (&state);
}

void	flipnet_capabilities_copy_app(t_flipnet_app *app,
			const t_flipnet_remote_app *source, uint8_t index, bool french)
{
	const char	*label;
	const char	*prompt;

	label = source->title_en;
	prompt = source->prompt_en;
	if (french)
	{
		label = source->title_fr;
		prompt = source->prompt_fr;
	}
	flipnet_copy(app->app_ids[index], sizeof(app->app_ids[index]), source->id);
	app->app_modes[index] = source->mode;
	flipnet_copy(app->app_labels[index], sizeof(app->app_labels[index]), label);
	flipnet_copy(app->app_prompts[index], sizeof(app->app_prompts[index]),
		prompt);
	flipnet_copy(app->app_initials[index], sizeof(app->app_initials[index]),
		source->initial);
}
