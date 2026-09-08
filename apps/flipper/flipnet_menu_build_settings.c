#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"
#include "flipnet_core.h"

void	flipnet_add(Submenu *menu, const char *label,
			uint32_t index, t_flipnet_app *app);
void	flipnet_menu_fill_utilities(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_settings(Submenu *menu, t_flipnet_app *app);

void	flipnet_add(Submenu *menu, const char *label,
			uint32_t index, t_flipnet_app *app)
{
	submenu_add_item(menu, label, index, flipnet_menu_callback, app);
}

void	flipnet_menu_fill_utilities(Submenu *menu, t_flipnet_app *app)
{
	flipnet_menu_build_parent(menu, app, "tools");
}

void	flipnet_menu_fill_settings(Submenu *menu, t_flipnet_app *app)
{
	flipnet_add(menu, flipnet_language_setting_label(app), 0U, app);
	flipnet_add(menu, flipnet_transport_setting_label(app), 1U, app);
	flipnet_add(menu, flipnet_tr(app,
			"Connection status", "Etat connexion"), 2U, app);
	flipnet_add(menu, flipnet_tr(app, "About", "A propos"), 3U, app);
	flipnet_add(menu, flipnet_tr(app,
			"Refresh features", "Actualiser fonctions"), 4U, app);
	if (flipnet_has_service(app, FLIPNET_FEATURE_EXTENSION_MANAGER_LIST))
		flipnet_add(menu, flipnet_tr(app,
				"Manage extensions", "Gerer les extensions"), 5U, app);
}

Submenu	*flipnet_menu_utilities(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Tools", "Outils"));
	flipnet_menu_fill_utilities(menu, app);
	return (menu);
}

Submenu	*flipnet_menu_settings(t_flipnet_app *app)
{
	Submenu	*menu;

	menu = flipnet_submenu(flipnet_tr(app, "Settings", "Reglages"));
	flipnet_menu_fill_settings(menu, app);
	return (menu);
}
