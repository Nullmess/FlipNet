#include "flipnet_i18n.h"
#include "flipnet_menu.h"

void	flipnet_secondary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index);
void	flipnet_menu_fill_security(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_systems(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_operations(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_actions(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_monitors(Submenu *menu, t_flipnet_app *app);

void	flipnet_secondary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index)
{
	submenu_add_item(menu, label, index, flipnet_menu_callback, app);
}

void	flipnet_menu_fill_security(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "network_inspection");
}

void	flipnet_menu_fill_systems(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "systems");
}

void	flipnet_menu_fill_operations(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "operations_overview");
	if (flipnet_menu_parent_has(app, "operations_monitoring"))
		flipnet_secondary_add_navigation(menu, app,
			flipnet_tr(app, "Monitoring", "Surveillance"),
			FLIPNET_MENU_AUTOMATION_MONITORING);
	if (flipnet_menu_parent_has(app, "operations_actions"))
		flipnet_secondary_add_navigation(menu, app,
			flipnet_tr(app, "Secure actions", "Actions securisees"),
			FLIPNET_MENU_AUTOMATION_ACTIONS);
}

void	flipnet_menu_fill_actions(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "operations_actions");
}
