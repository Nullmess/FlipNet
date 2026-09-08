#include "flipnet_core.h"
#include "flipnet_history_i.h"

#include <stdlib.h>
#include <string.h>

void	flipnet_history_save(t_flipnet_app *app);
uint8_t	flipnet_history_slot(t_flipnet_app *app, const char *key);

static void	flipnet_history_shift(t_flipnet_app *app, uint8_t *index)
{
	memmove(app->history_keys, app->history_keys + 1,
		sizeof(app->history_keys[0]) * (FLIPNET_HISTORY_ITEMS - 1U));
	memmove(app->history_values, app->history_values + 1,
		sizeof(app->history_values[0]) * (FLIPNET_HISTORY_ITEMS - 1U));
	*index = FLIPNET_HISTORY_ITEMS - 1U;
	app->history_count = FLIPNET_HISTORY_ITEMS;
}

void	flipnet_history_set(t_flipnet_app *app, const char *key,
			const char *value)
{
	uint8_t	index;

	if (!app || !key || !key[0] || !value || !value[0])
		return ;
	index = flipnet_history_slot(app, key);
	if (index >= FLIPNET_HISTORY_ITEMS)
		flipnet_history_shift(app, &index);
	else if (index == app->history_count)
		app->history_count++;
	flipnet_copy(app->history_keys[index], sizeof(app->history_keys[index]),
		key);
	flipnet_copy(app->history_values[index],
		sizeof(app->history_values[index]), value);
	flipnet_history_save(app);
}

size_t	flipnet_history_read(char *buffer)
{
	Storage	*storage;
	File	*file;
	size_t	size;

	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	size = 0U;
	if (file && storage_file_open(file, FLIPNET_HISTORY_PATH,
			FSAM_READ, FSOM_OPEN_EXISTING))
		size = storage_file_read(file, buffer, FLIPNET_HISTORY_FILE_SIZE);
	if (file)
	{
		storage_file_close(file);
		storage_file_free(file);
	}
	furi_record_close(RECORD_STORAGE);
	return (size);
}

static void	flipnet_history_add_line(t_flipnet_app *app, char *line)
{
	char	*separator;

	separator = strchr(line, '=');
	if (!separator)
		return ;
	*separator++ = '\0';
	if (!line[0] || !separator[0])
		return ;
	flipnet_copy(app->history_keys[app->history_count],
		sizeof(app->history_keys[0]), line);
	flipnet_copy(app->history_values[app->history_count],
		sizeof(app->history_values[0]), separator);
	app->history_count++;
}

void	flipnet_history_parse(t_flipnet_app *app, char *buffer)
{
	char	*line;
	char	*next;

	line = buffer;
	while (line && *line && app->history_count < FLIPNET_HISTORY_ITEMS)
	{
		next = strchr(line, '\n');
		if (next)
			*next++ = '\0';
		flipnet_history_add_line(app, line);
		line = next;
	}
}
