#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_ui.h"

#include <stdlib.h>
#include <string.h>

bool	flipnet_request_try(t_flipnet_app *app);
void	flipnet_response_process(t_flipnet_app *app,
			t_flipnet_response *response);

void	flipnet_event_timeout(t_flipnet_app *app);
uint16_t	flipnet_request_timeout_frames(t_flipnet_app *app);
void	flipnet_event_animation(t_flipnet_app *app);
void	flipnet_event_transport(t_flipnet_app *app);
void	flipnet_event_line(t_flipnet_app *app);

bool	flipnet_custom_event(void *context, uint32_t event)
{
	t_flipnet_app	*app;

	app = context;
	if (event == EVENT_ANIMATION)
		flipnet_event_animation(app);
	else if (event == EVENT_TRANSPORT)
		flipnet_event_transport(app);
	else if (event == EVENT_LINE)
		flipnet_event_line(app);
	else
		return (false);
	return (true);
}
