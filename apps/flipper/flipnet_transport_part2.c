#include "flipnet_core.h"
#include "flipnet_i18n.h"

#include <storage/storage.h>

#define FLIPNET_TRANSPORT_PATH APP_DATA_PATH("transport.txt")

void	flipnet_transport_save(t_flipnet_app *app);
t_flipnet_transport	flipnet_transport_resolve(t_flipnet_app *app);

t_flipnet_transport	flipnet_transport_choose(t_flipnet_app *app)
{
	if (!app)
		return (TRANSPORT_NONE);
	return (app->active_transport);
}

void	flipnet_transport_cycle(t_flipnet_app *app)
{
	t_flipnet_transport	current;

	if (app->mode == MODE_USB)
		app->mode = MODE_BLE;
	else if (app->mode == MODE_BLE)
		app->mode = MODE_AUTO;
	else
		app->mode = MODE_USB;
	flipnet_transport_save(app);
	flipnet_transport_refresh(app);
	submenu_change_item_label(app->settings, 1U,
		flipnet_transport_setting_label(app));
	current = flipnet_transport_choose(app);
	if (current == TRANSPORT_NONE)
		return ;
	if (app->capabilities->loaded
		&& app->catalog_transport != TRANSPORT_NONE
		&& current != app->catalog_transport)
		flipnet_capabilities_invalidate(app);
	if (!app->capabilities->loaded && !app->capabilities_attempted
		&& !app->animation)
		flipnet_capabilities_refresh_background(app);
}
