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
void	flipnet_menu_home_main(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_home_extra(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_internet(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_network(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_discovery(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_ports(Submenu *menu, t_flipnet_app *app);

void	flipnet_primary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index)
{
	submenu_add_item(menu, label, index, flipnet_menu_callback, app);
}

bool	flipnet_menu_network_available(t_flipnet_app *app)
{
	return (flipnet_menu_parent_has(app, "network_inspection")
		|| flipnet_menu_parent_has(app, "network_discovery")
		|| flipnet_menu_parent_has(app, "network_inventory"));
}

bool	flipnet_menu_automation_available(t_flipnet_app *app)
{
	return (flipnet_menu_parent_has(app, "operations_overview")
		|| flipnet_menu_parent_has(app, "operations_monitoring")
		|| flipnet_menu_parent_has(app, "operations_actions"));
}

void	flipnet_menu_fill_language(Submenu *menu, t_flipnet_app *app)
{
	flipnet_primary_add_navigation(menu, app, "English", 0U);
	flipnet_primary_add_navigation(menu, app, "Francais", 1U);
}

void	flipnet_menu_fill_home(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "home");
	flipnet_menu_home_main(menu, app);
	flipnet_menu_home_extra(menu, app);
}

