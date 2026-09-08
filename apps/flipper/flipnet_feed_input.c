#include "flipnet_features_generated.h"
#include "flipnet_app.h"
#include "flipnet_core.h"

#include <stdio.h>

static bool	flipnet_feed_argument(t_feed_model *model, char *argument)
{
	if (model->count == 0U)
		return (false);
	snprintf(argument, 40U, "%s|%u", model->sources[model->index],
		model->source_indices[model->index]);
	return (true);
}

static bool	flipnet_feed_move(t_feed_model *model, InputKey key)
{
	if (key == InputKeyLeft && model->index > 0U)
	{
		model->index--;
		return (true);
	}
	if (key == InputKeyRight && model->index + 1U < model->count)
	{
		model->index++;
		return (true);
	}
	return (false);
}

bool	flipnet_feed_input(InputEvent *event, void *context)
{
	t_flipnet_app	*app;
	t_feed_model	*model;
	bool			changed;
	bool			open;
	char			argument[40];

	app = context;
	if (event->type != InputTypeShort && event->type != InputTypeRepeat)
		return (false);
	model = view_get_model(app->feed_cards);
	changed = flipnet_feed_move(model, event->key);
	open = event->key == InputKeyOk;
	if (open)
		open = flipnet_feed_argument(model, argument);
	view_commit_model(app->feed_cards, changed);
	if (open)
		flipnet_request_direct(app, VIEW_FEED_CARDS,
			FLIPNET_FEATURE_FEED_ARTICLE, argument);
	return (changed || open);
}
