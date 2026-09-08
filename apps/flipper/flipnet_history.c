#include "flipnet_core.h"
#include "flipnet_history_i.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static size_t	flipnet_history_format(t_flipnet_app *app, char *buffer)
{
	size_t	used;
	uint8_t	index;
	int		written;

	used = 0U;
	index = 0U;
	while (index < app->history_count && used < FLIPNET_HISTORY_FILE_SIZE)
	{
		written = snprintf(buffer + used, FLIPNET_HISTORY_FILE_SIZE - used,
				"%s=%s\n", app->history_keys[index],
				app->history_values[index]);
		if (written < 0
			|| (size_t)written >= FLIPNET_HISTORY_FILE_SIZE - used)
			break ;
		used += (size_t)written;
		index++;
	}
	return (used);
}

static void	flipnet_history_write(const char *buffer, size_t size)
{
	Storage	*storage;
	File	*file;

	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	if (file && storage_file_open(file, FLIPNET_HISTORY_PATH,
			FSAM_WRITE, FSOM_CREATE_ALWAYS))
		storage_file_write(file, buffer, size);
	if (file)
	{
		storage_file_close(file);
		storage_file_free(file);
	}
	furi_record_close(RECORD_STORAGE);
}

void	flipnet_history_save(t_flipnet_app *app)
{
	char	*buffer;
	size_t	used;

	if (!app)
		return ;
	buffer = malloc(FLIPNET_HISTORY_FILE_SIZE);
	if (!buffer)
		return ;
	used = flipnet_history_format(app, buffer);
	flipnet_history_write(buffer, used);
	free(buffer);
}

const char	*flipnet_history_get(t_flipnet_app *app, const char *key)
{
	uint8_t	index;

	if (!app || !key || !key[0])
		return (NULL);
	index = 0U;
	while (index < app->history_count)
	{
		if (!strcmp(app->history_keys[index], key))
			return (app->history_values[index]);
		index++;
	}
	return (NULL);
}

uint8_t	flipnet_history_slot(t_flipnet_app *app, const char *key)
{
	uint8_t	index;

	index = 0U;
	while (index < app->history_count)
	{
		if (!strcmp(app->history_keys[index], key))
			return (index);
		index++;
	}
	return (index);
}
