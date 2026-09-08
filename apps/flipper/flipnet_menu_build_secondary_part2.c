#include "flipnet_i18n.h"
#include "flipnet_menu.h"

void	flipnet_secondary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index);
void	flipnet_menu_fill_security(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_systems(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_operations(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_actions(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_monitors(Submenu *menu, t_flipnet_app *app);

void	flipnet_menu_fill_monitors(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "operations_monitoring");
}

Submenu	*flipnet_menu_security(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app,
			"Network inspection", "Inspection reseau"));
	flipnet_menu_fill_security(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_systems(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Device", "Appareil"));
	flipnet_menu_fill_systems(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_operations(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Automation", "Automatisation"));
	flipnet_menu_fill_operations(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_actions(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app,
			"Secure actions", "Actions securisees"));
	flipnet_menu_fill_actions(menu, app);
	return (menu);
}
