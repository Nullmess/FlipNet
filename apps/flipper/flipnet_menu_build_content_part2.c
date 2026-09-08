#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"

void	flipnet_menu_feeds_french(Submenu *menu,
			t_flipnet_app *app);
void	flipnet_menu_feeds_english(Submenu *menu,
			t_flipnet_app *app);
void	flipnet_menu_fill_feeds(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_web(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_workspace(Submenu *menu, t_flipnet_app *app);

Submenu	*flipnet_menu_feeds(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app,
			"News and RSS", "Actualites et RSS"));
	flipnet_menu_fill_feeds(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_feeds_fr(t_flipnet_app *app)
{
	return (flipnet_menu_feeds(app));
}

Submenu	*flipnet_menu_feeds_en(t_flipnet_app *app)
{
	return (flipnet_menu_feeds(app));
}

Submenu	*flipnet_menu_web(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu("Web");
	flipnet_menu_fill_web(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_workspace(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Workspace", "Espace"));
	flipnet_menu_fill_workspace(menu, app);
	return (menu);
}
