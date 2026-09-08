#include "flipnet_i18n.h"
#include "flipnet_menu.h"

void	flipnet_secondary_add_navigation(Submenu *menu,
			t_flipnet_app *app, const char *label, uint32_t index);
void	flipnet_menu_fill_security(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_systems(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_operations(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_actions(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_monitors(Submenu *menu, t_flipnet_app *app);

Submenu	*flipnet_menu_monitors(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Monitoring", "Surveillance"));
	flipnet_menu_fill_monitors(menu, app);
	return (menu);
}

void	flipnet_menu_refresh_secondary(t_flipnet_app *app)
{
	submenu_reset(app->security);
	submenu_set_header(app->security,
		flipnet_tr(app, "Network inspection", "Inspection reseau"));
	flipnet_menu_fill_security(app->security, app);
	submenu_reset(app->systems);
	submenu_set_header(app->systems,
		flipnet_tr(app, "Device", "Appareil"));
	flipnet_menu_fill_systems(app->systems, app);
	submenu_reset(app->operations);
	submenu_set_header(app->operations,
		flipnet_tr(app, "Automation", "Automatisation"));
	flipnet_menu_fill_operations(app->operations, app);
	submenu_reset(app->actions);
	submenu_set_header(app->actions,
		flipnet_tr(app, "Secure actions", "Actions securisees"));
	flipnet_menu_fill_actions(app->actions, app);
	submenu_reset(app->monitors);
	submenu_set_header(app->monitors,
		flipnet_tr(app, "Monitoring", "Surveillance"));
	flipnet_menu_fill_monitors(app->monitors, app);
}
