#ifndef FLIPNET_CAPABILITIES_I_H
# define FLIPNET_CAPABILITIES_I_H

# include "flipnet_capability_catalog.h"
# include "flipnet_core.h"

typedef struct s_flipnet_capability_state
{
	t_flipnet_capabilities	current;
	t_flipnet_capabilities	pending;
	bool				initialized;
	bool				resume_apps;
	t_flipnet_transport		transport;
} t_flipnet_capability_state;

t_flipnet_capability_state	*flipnet_capabilities_state(void);
void					flipnet_capabilities_apps(t_flipnet_app *app);
void					flipnet_capabilities_copy_app(t_flipnet_app *app,
						const t_flipnet_remote_app *source, uint8_t index,
						bool french);
void					flipnet_capabilities_prepare(t_flipnet_app *app);

#endif
