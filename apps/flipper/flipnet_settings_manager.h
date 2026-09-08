#ifndef FLIPNET_SETTINGS_MANAGER_H
# define FLIPNET_SETTINGS_MANAGER_H

# include "flipnet_app.h"

void	flipnet_extension_settings_open(t_flipnet_app *app);

void	flipnet_plugin_settings_open(t_flipnet_app *app);

bool	flipnet_extension_settings_show(t_flipnet_app *app, const char *body);

bool	flipnet_plugin_settings_show(t_flipnet_app *app, const char *body);

void	flipnet_extension_settings_callback(void *context, uint32_t index);

void	flipnet_plugin_settings_callback(void *context, uint32_t index);

void	flipnet_plugin_action_callback(void *context, uint32_t index);

void	flipnet_plugin_confirm_callback(void *context, uint32_t index);

void	flipnet_settings_management_changed(t_flipnet_app *app);

#endif
