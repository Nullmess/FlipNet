#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"
#include "flipnet_core.h"

void	flipnet_primary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index);
bool	flipnet_menu_network_available(t_flipnet_app *app);
bool	flipnet_menu_automation_available(t_flipnet_app *app);
void	flipnet_menu_fill_language(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_home(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_internet(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_network(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_discovery(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_ports(Submenu *menu, t_flipnet_app *app);

void	flipnet_menu_fill_internet(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "internet");
}

void	flipnet_menu_fill_network(Submenu *menu, t_flipnet_app *app)
{
	if (flipnet_menu_parent_has(app, "network_inspection"))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Inspection", "Inspection"),
			FLIPNET_MENU_NETWORK_INSPECTION);
	if (flipnet_menu_parent_has(app, "network_discovery"))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Discovery", "Decouverte"),
			FLIPNET_MENU_NETWORK_DISCOVERY);
	if (flipnet_menu_parent_has(app, "network_inventory"))
		flipnet_primary_add_navigation(menu, app,
			flipnet_tr(app, "Inventory", "Inventaire"),
			FLIPNET_MENU_NETWORK_INVENTORY);
}

void	flipnet_menu_fill_discovery(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "network_discovery");
}

void	flipnet_menu_fill_ports(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "network_inventory");
}

Submenu	*flipnet_menu_language(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu("Language / Langue");
	flipnet_menu_fill_language(menu, app);
	return (menu);
}
