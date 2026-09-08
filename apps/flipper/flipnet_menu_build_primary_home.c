#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"
#include "flipnet_core.h"

void	flipnet_primary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index);
bool	flipnet_menu_network_available(t_flipnet_app *app);
bool	flipnet_menu_automation_available(t_flipnet_app *app);

void	flipnet_menu_home_main(Submenu *menu, t_flipnet_app *app)
{
	if (flipnet_menu_network_available(app))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Network", "Reseau"),
			FLIPNET_MENU_HOME_NETWORK);
	if (flipnet_menu_parent_has(app, "systems"))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Device", "Appareil"),
			FLIPNET_MENU_HOME_DEVICE);
	if (flipnet_menu_parent_has(app, "internet"))
		flipnet_primary_add_navigation(menu, app, "Internet",
			FLIPNET_MENU_HOME_INTERNET);
	if (flipnet_menu_parent_has(app, "tools"))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Tools", "Outils"),
			FLIPNET_MENU_HOME_TOOLS);
}

void	flipnet_menu_home_extra(Submenu *menu, t_flipnet_app *app)
{
	if (flipnet_menu_automation_available(app))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Automation", "Automatisation"),
			FLIPNET_MENU_HOME_AUTOMATION);
	if (flipnet_menu_parent_has(app, "workspace"))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Workspace", "Espace"),
			FLIPNET_MENU_HOME_WORKSPACE);
	if (flipnet_has_service(app, FLIPNET_FEATURE_PLUGIN_MANAGER_LIST))
		flipnet_primary_add_navigation(menu, app, "Plugins",
			FLIPNET_MENU_HOME_PLUGINS);
	flipnet_primary_add_navigation(menu, app,
		flipnet_tr(app, "Settings", "Reglages"),
		FLIPNET_MENU_HOME_SETTINGS);
}
