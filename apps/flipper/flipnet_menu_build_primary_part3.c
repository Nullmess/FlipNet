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

Submenu	*flipnet_menu_home(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu("FlipNet");
	flipnet_menu_fill_home(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_internet(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu("Internet");
	flipnet_menu_fill_internet(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_network(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Network", "Reseau"));
	flipnet_menu_fill_network(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_discovery(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Discovery", "Decouverte"));
	flipnet_menu_fill_discovery(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_ports(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Inventory", "Inventaire"));
	flipnet_menu_fill_ports(menu, app);
	return (menu);
}
