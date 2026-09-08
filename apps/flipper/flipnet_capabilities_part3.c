#include "flipnet_capabilities_i.h"

void	flipnet_capabilities_refresh_apps(t_flipnet_app *app)
{
	t_flipnet_capability_state	*state;

	if (!app)
		return ;
	state = flipnet_capabilities_state();
	state->resume_apps = true;
	flipnet_capabilities_invalidate(app);
	flipnet_capabilities_refresh(app);
}

bool	flipnet_capabilities_resume_apps(void)
{
	t_flipnet_capability_state	*state;
	bool				resume;

	state = flipnet_capabilities_state();
	resume = state->resume_apps;
	state->resume_apps = false;
	return (resume);
}
