#ifndef FLIPNET_FILES_H
# define FLIPNET_FILES_H

# include "flipnet_app.h"

# include <stdbool.h>

bool	flipnet_files_handle_line(t_flipnet_app *app, const char *line);
void	flipnet_files_reset(t_flipnet_app *app);

#endif
