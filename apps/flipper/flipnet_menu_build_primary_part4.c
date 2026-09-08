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

void	flipnet_menu_refresh_primary(t_flipnet_app *app)
{
	submenu_reset(app->home);
	submenu_set_header(app->home, "FlipNet");
	flipnet_menu_fill_home(app->home, app);
	submenu_reset(app->internet);
	submenu_set_header(app->internet, "Internet");
	flipnet_menu_fill_internet(app->internet, app);
	submenu_reset(app->network);
	submenu_set_header(app->network, flipnet_tr(app, "Network", "Reseau"));
	flipnet_menu_fill_network(app->network, app);
	submenu_reset(app->discovery);
	submenu_set_header(app->discovery,
		flipnet_tr(app, "Discovery", "Decouverte"));
	flipnet_menu_fill_discovery(app->discovery, app);
	submenu_reset(app->ports);
	submenu_set_header(app->ports,
		flipnet_tr(app, "Inventory", "Inventaire"));
	flipnet_menu_fill_ports(app->ports, app);
}
