#include "flipnet_core.h"
#include "flipnet_history_i.h"

#include <stdlib.h>

size_t	flipnet_history_read(char *buffer);
void	flipnet_history_parse(t_flipnet_app *app, char *buffer);

void	flipnet_history_load(t_flipnet_app *app)
{
	char	*buffer;
	size_t	size;

	if (!app)
		return ;
	app->history_count = 0U;
	buffer = malloc(FLIPNET_HISTORY_FILE_SIZE + 1U);
	if (!buffer)
		return ;
	size = flipnet_history_read(buffer);
	if (size)
	{
		buffer[size] = '\0';
		flipnet_history_parse(app, buffer);
	}
	free(buffer);
}
