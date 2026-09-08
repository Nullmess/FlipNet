#include "flipnet_core.h"
#include "flipnet_ui.h"

#include <stdlib.h>
#include <string.h>

static bool	flipnet_feed_line(const char **cursor, char *output, size_t size)
{
	size_t	length;

	if (!cursor || !*cursor || !**cursor)
		return (false);
	length = 0U;
	while ((*cursor)[length] && (*cursor)[length] != '\n'
		&& length + 1U < size)
		length++;
	memcpy(output, *cursor, length);
	output[length] = '\0';
	*cursor += length;
	if (**cursor == '\n')
		(*cursor)++;
	return (true);
}

static bool	flipnet_feed_item(t_feed_model *model, char *line, uint8_t index)
{
	char	*first;
	char	*second;

	if (strncmp(line, "Item=", 5U))
		return (false);
	first = strchr(line + 5U, '|');
	if (!first)
		return (false);
	*first = '\0';
	second = strchr(first + 1, '|');
	if (!second)
		return (false);
	*second = '\0';
	flipnet_copy(model->sources[index], FLIPNET_FEED_SOURCE, line + 5U);
	model->source_indices[index] = (uint8_t)strtoul(first + 1, NULL, 10);
	flipnet_copy(model->titles[index], FLIPNET_FEED_TITLE, second + 1);
	return (true);
}

bool	flipnet_feed_update(t_flipnet_app *app, const char *body)
{
	t_feed_model	*model;
	const char		*cursor;
	char			line[220];
	uint8_t			count;

	model = view_get_model(app->feed_cards);
	memset(model, 0, sizeof(t_feed_model));
	cursor = body;
	if (!flipnet_feed_line(&cursor, line, sizeof(line))
		|| strcmp(line, "FNFEED1"))
		return (view_commit_model(app->feed_cards, false), false);
	count = 0U;
	while (flipnet_feed_line(&cursor, line, sizeof(line)))
	{
		if (!strncmp(line, "Title=", 6U))
			flipnet_copy(model->heading, sizeof(model->heading), line + 6U);
		else if (count < FLIPNET_FEED_ITEMS
			&& flipnet_feed_item(model, line, count))
			count++;
	}
	model->count = count;
	model->index = 0U;
	view_commit_model(app->feed_cards, true);
	return (count > 0U);
}
