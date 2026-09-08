#include "flipnet_core.h"
#include "flipnet_menu.h"
#include "flipnet_settings_manager.h"
#include "flipnet_ui.h"

void	flipnet_menu_home_action(t_flipnet_app *app, uint32_t index)
{
	if (index == FLIPNET_MENU_HOME_NETWORK)
		flipnet_switch(app, VIEW_NETWORK);
	else if (index == FLIPNET_MENU_HOME_DEVICE)
		flipnet_switch(app, VIEW_SYSTEMS);
	else if (index == FLIPNET_MENU_HOME_INTERNET)
		flipnet_switch(app, VIEW_INTERNET);
	else if (index == FLIPNET_MENU_HOME_TOOLS)
		flipnet_switch(app, VIEW_UTILITIES);
	else if (index == FLIPNET_MENU_HOME_AUTOMATION)
		flipnet_switch(app, VIEW_OPERATIONS);
	else if (index == FLIPNET_MENU_HOME_WORKSPACE)
		flipnet_switch(app, VIEW_WORKSPACE);
	else if (index == FLIPNET_MENU_HOME_PLUGINS)
		flipnet_plugin_settings_open(app);
	else if (index == FLIPNET_MENU_HOME_SETTINGS)
		flipnet_switch(app, VIEW_SETTINGS);
}

void	flipnet_menu_network_action(t_flipnet_app *app, uint32_t index)
{
	if (index == FLIPNET_MENU_NETWORK_INSPECTION)
		flipnet_switch(app, VIEW_SECURITY);
	else if (index == FLIPNET_MENU_NETWORK_DISCOVERY)
		flipnet_switch(app, VIEW_DISCOVERY);
	else if (index == FLIPNET_MENU_NETWORK_INVENTORY)
		flipnet_switch(app, VIEW_PORTS);
}
