#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_settings_manager.h"
#include "flipnet_ui.h"

void	flipnet_menu_settings_action(t_flipnet_app *app, uint32_t index)
{
	if (index == 0U)
	{
		app->return_view = VIEW_SETTINGS;
		flipnet_switch(app, VIEW_LANGUAGE);
	}
	else if (index == 1U)
		flipnet_transport_cycle(app);
	else if (index == 2U)
		flipnet_diagnostics_show(app);
	else if (index == 3U)
	{
		app->return_view = VIEW_SETTINGS;
		flipnet_result_set(app, "FlipNet " FLIPNET_VERSION,
			flipnet_tr(app,
				"Gateway tools over USB or Bluetooth.\n"
				"PC phone server and more.",
				"Outils passerelle via USB ou Bluetooth.\n"
				"PC telephone serveur et plus."));
		flipnet_switch(app, VIEW_RESULT);
	}
	else if (index == 4U)
		flipnet_capabilities_refresh(app);
	else if (index == 5U)
		flipnet_extension_settings_open(app);
}
