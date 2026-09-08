#ifndef FLIPNET_APPS_I_H
# define FLIPNET_APPS_I_H

# include "flipnet_catalog.h"
# include "flipnet_core.h"

typedef struct s_flipnet_app_input
{
	const char	*service;
	const char	*history_key;
	const char	*initial;
	const char	*header;
} t_flipnet_app_input;

typedef struct s_flipnet_choice_output
{
	char	*data;
	size_t	size;
	bool	trailing_space;
} t_flipnet_choice_output;

bool	flipnet_apps_prefix(t_flipnet_app *app, const char *app_id);
void	flipnet_apps_open_input(t_flipnet_app *app,
			const t_flipnet_app_input *input);
bool	flipnet_apps_input(t_flipnet_app *app, uint8_t index,
			const char *service);
void	flipnet_apps_callback(void *context, uint32_t index);
void	flipnet_apps_menu(t_flipnet_app *app);
bool	flipnet_apps_choice_item(t_flipnet_app *app, t_flipnet_slice line);
bool	flipnet_apps_choice_argument(t_flipnet_app *app, uint8_t index,
			t_flipnet_choice_output *output);
void	flipnet_apps_translate_target(t_flipnet_app *app, uint8_t index);
void	flipnet_apps_choice_menu(t_flipnet_app *app);

#endif
