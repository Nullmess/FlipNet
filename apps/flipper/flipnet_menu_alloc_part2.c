#include "flipnet_menu.h"

Submenu	*flipnet_menu_language(t_flipnet_app *app);
Submenu	*flipnet_menu_home(t_flipnet_app *app);
Submenu	*flipnet_menu_internet(t_flipnet_app *app);
Submenu	*flipnet_menu_network(t_flipnet_app *app);
Submenu	*flipnet_menu_discovery(t_flipnet_app *app);
Submenu	*flipnet_menu_ports(t_flipnet_app *app);
Submenu	*flipnet_menu_security(t_flipnet_app *app);
Submenu	*flipnet_menu_systems(t_flipnet_app *app);
Submenu	*flipnet_menu_operations(t_flipnet_app *app);
Submenu	*flipnet_menu_actions(t_flipnet_app *app);
Submenu	*flipnet_menu_monitors(t_flipnet_app *app);
Submenu	*flipnet_menu_feeds(t_flipnet_app *app);
Submenu	*flipnet_menu_feeds_fr(t_flipnet_app *app);
Submenu	*flipnet_menu_feeds_en(t_flipnet_app *app);
Submenu	*flipnet_menu_web(t_flipnet_app *app);
Submenu	*flipnet_menu_workspace(t_flipnet_app *app);
Submenu	*flipnet_menu_targets(t_flipnet_app *app);
Submenu	*flipnet_menu_utilities(t_flipnet_app *app);
Submenu	*flipnet_menu_settings(t_flipnet_app *app);
void	flipnet_menu_refresh_primary(t_flipnet_app *app);
void	flipnet_menu_refresh_secondary(t_flipnet_app *app);
void	flipnet_menu_refresh_content(t_flipnet_app *app);
void	flipnet_menu_refresh_settings(t_flipnet_app *app);

void	flipnet_menu_alloc_primary(t_flipnet_app *app);
void	flipnet_menu_alloc_system(t_flipnet_app *app);
void	flipnet_menu_alloc_content(t_flipnet_app *app);
void	flipnet_menu_alloc_settings(t_flipnet_app *app);

void	flipnet_menus_refresh(t_flipnet_app *app)
{
	if (!app)
		return ;
	app->catalog_refresh_pending = false;
	flipnet_menu_refresh_primary(app);
	flipnet_menu_refresh_secondary(app);
	flipnet_menu_refresh_content(app);
	flipnet_menu_refresh_settings(app);
}
