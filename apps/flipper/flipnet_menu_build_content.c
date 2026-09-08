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



void	flipnet_menu_fill_feeds(Submenu *menu, t_flipnet_app *app)
{
	if (app->language == LANGUAGE_FR)
		flipnet_menu_feeds_french(menu, app);
	else
		flipnet_menu_feeds_english(menu, app);
}

void	flipnet_menu_fill_web(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "internet");
}

void	flipnet_menu_fill_workspace(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "workspace");
}
