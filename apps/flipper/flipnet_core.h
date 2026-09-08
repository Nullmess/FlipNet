#ifndef FLIPNET_CORE_H
# define FLIPNET_CORE_H

# include "flipnet_app.h"

void	flipnet_capabilities_load(t_flipnet_app *app);

t_flipnet_catalog_result	flipnet_capabilities_update(t_flipnet_app *app,
			const char *payload);

uint8_t	flipnet_capabilities_next_page(void);

bool	flipnet_has_service(const t_flipnet_app *app, const char *service);

bool	flipnet_has_any_service(const t_flipnet_app *app,
			const char *const *services, size_t count);

void	flipnet_capabilities_invalidate(t_flipnet_app *app);

void	flipnet_capabilities_refresh(t_flipnet_app *app);

void	flipnet_capabilities_refresh_background(t_flipnet_app *app);

void	flipnet_capabilities_refresh_apps(t_flipnet_app *app);

bool	flipnet_capabilities_resume_apps(void);

void	flipnet_copy(char *destination, size_t size, const char *source);

void	flipnet_switch(t_flipnet_app *app, t_flipnet_view view);

void	flipnet_cancel(t_flipnet_app *app);

bool	flipnet_navigation(void *context);

bool	flipnet_custom_event(void *context, uint32_t event);

void	flipnet_line_callback(const char *line, void *context);

void	flipnet_ble_callback(bool connected, bool advertising, void *context);

void	flipnet_usb_callback(bool connected, void *context);

void	flipnet_request_direct(t_flipnet_app *app, t_flipnet_view parent,
			const char *service, const char *argument);

void	flipnet_request_direct_background(t_flipnet_app *app,
			t_flipnet_view parent, const char *service, const char *argument);

void	flipnet_input_open(t_flipnet_app *app, t_input_spec spec);

void	flipnet_input_done(void *context);

void	flipnet_history_load(t_flipnet_app *app);

const char	*flipnet_history_get(t_flipnet_app *app, const char *key);

void	flipnet_history_set(t_flipnet_app *app, const char *key,
			const char *value);

void	flipnet_transport_load(t_flipnet_app *app);

const char	*flipnet_transport_setting_label(t_flipnet_app *app);

void	flipnet_transport_cycle(t_flipnet_app *app);

t_flipnet_transport	flipnet_transport_choose(t_flipnet_app *app);

void	flipnet_transport_refresh(t_flipnet_app *app);

void	flipnet_animation_start(t_flipnet_app *app);

void	flipnet_background_start(t_flipnet_app *app);

void	flipnet_animation_stop(t_flipnet_app *app);

void	flipnet_animation_callback(void *context);

#endif
