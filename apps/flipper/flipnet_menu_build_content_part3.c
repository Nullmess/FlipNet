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

Submenu	*flipnet_menu_targets(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Workspace", "Espace"));
	flipnet_menu_fill_workspace(menu, app);
	return (menu);
}

void	flipnet_menu_refresh_content(t_flipnet_app *app)
{
	submenu_reset(app->feeds);
	submenu_set_header(app->feeds,
		flipnet_tr(app, "News and RSS", "Actualites et RSS"));
	flipnet_menu_fill_feeds(app->feeds, app);
	submenu_reset(app->feeds_fr);
	submenu_set_header(app->feeds_fr,
		flipnet_tr(app, "News and RSS", "Actualites et RSS"));
	flipnet_menu_fill_feeds(app->feeds_fr, app);
	submenu_reset(app->feeds_en);
	submenu_set_header(app->feeds_en,
		flipnet_tr(app, "News and RSS", "Actualites et RSS"));
	flipnet_menu_fill_feeds(app->feeds_en, app);
	submenu_reset(app->web);
	submenu_set_header(app->web, "Web");
	flipnet_menu_fill_web(app->web, app);
	submenu_reset(app->workspace);
	submenu_set_header(app->workspace,
		flipnet_tr(app, "Workspace", "Espace"));
	flipnet_menu_fill_workspace(app->workspace, app);
	submenu_reset(app->targets);
	submenu_set_header(app->targets,
		flipnet_tr(app, "Workspace", "Espace"));
	flipnet_menu_fill_workspace(app->targets, app);
}
