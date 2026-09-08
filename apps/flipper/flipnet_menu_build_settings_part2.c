#include "flipnet_features_generated.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"
#include "flipnet_core.h"

void	flipnet_add(Submenu *menu, const char *label,
			uint32_t index, t_flipnet_app *app);
void	flipnet_menu_fill_utilities(Submenu *menu, t_flipnet_app *app);
void	flipnet_menu_fill_settings(Submenu *menu, t_flipnet_app *app);

void	flipnet_menu_refresh_settings(t_flipnet_app *app)
{
	submenu_reset(app->utilities);
	submenu_set_header(app->utilities,
		flipnet_tr(app, "Tools", "Outils"));
	flipnet_menu_fill_utilities(app->utilities, app);
	submenu_reset(app->settings);
	submenu_set_header(app->settings,
		flipnet_tr(app, "Settings", "Reglages"));
	flipnet_menu_fill_settings(app->settings, app);
}
