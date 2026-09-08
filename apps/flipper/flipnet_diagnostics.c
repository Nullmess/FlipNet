#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <stdio.h>

static const char	*flipnet_diagnostic_link(t_flipnet_app *app)
{
	if (app->usb_connected && app->ble_connected)
		return ("USB + BLE");
	if (app->usb_connected)
		return ("USB");
	if (app->ble_connected)
		return ("BLE");
	return (flipnet_tr(app, "Offline", "Hors ligne"));
}

static const char	*flipnet_diagnostic_catalog(t_flipnet_app *app)
{
	if (!app->capabilities || !app->capabilities->loaded)
		return (flipnet_tr(app, "Unknown", "Inconnu"));
	return ("FNCAP2");
}

static const char	*flipnet_diagnostic_error(t_flipnet_app *app)
{
	if (app->last_error[0])
		return (app->last_error);
	return (flipnet_tr(app, "None", "Aucune"));
}

void	flipnet_diagnostics_show(t_flipnet_app *app)
{
	char	body[512];
	size_t	queued;

	flipnet_transport_refresh(app);
	queued = furi_message_queue_get_count(app->lines);
	snprintf(body, sizeof(body),
		flipnet_tr(app,
			"FAP: %s\nProtocol: %s\nCatalog: %s\nLink: %s\nQueue: %u/%u\n"
			"Pending: %lu\nLast error: %s",
			"FAP : %s\nProtocole : %s\nCatalogue : %s\nLien : %s\n"
			"File : %u/%u\nEn attente : %lu\nDerniere erreur : %s"),
		FLIPNET_VERSION, FLIPNET_PROTOCOL_VERSION,
		flipnet_diagnostic_catalog(app), flipnet_diagnostic_link(app),
		(unsigned int)queued, FLIPNET_QUEUE_SIZE,
		(unsigned long)app->pending_request,
		flipnet_diagnostic_error(app));
	app->return_view = VIEW_SETTINGS;
	flipnet_result_set(app,
		flipnet_tr(app, "Connection status", "Etat connexion"), body);
	flipnet_switch(app, VIEW_RESULT);
}
