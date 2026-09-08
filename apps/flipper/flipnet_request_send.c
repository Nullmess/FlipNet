#include "flipnet_core.h"
#include "flipnet_ui.h"
#include "flipnet_i18n.h"

#include <stdio.h>
#include <string.h>

static bool	flipnet_transport_send(t_flipnet_app *app,
			t_flipnet_transport transport)
{
	t_ble_payload	ble_data;
	t_usb_payload	usb_data;

	if (transport == TRANSPORT_USB)
	{
		usb_data.data = app->tx_buffer;
		usb_data.length = strlen(app->tx_buffer);
		return (flipnet_usb_send(app->usb, usb_data));
	}
	ble_data.data = app->tx_buffer;
	ble_data.length = strlen(app->tx_buffer);
	return (flipnet_ble_send(app->ble, ble_data));
}

static bool	flipnet_request_build(t_flipnet_app *app, uint32_t identifier)
{
	t_flipnet_request	request;
	int				written;

	written = snprintf(app->wire_argument, sizeof(app->wire_argument),
			"@%s:%s", flipnet_language_code(app), app->pending_argument);
	if (written <= 0 || (size_t)written >= sizeof(app->wire_argument))
		return (false);
	request.output = app->tx_buffer;
	request.output_size = sizeof(app->tx_buffer);
	request.session_id = app->session_id;
	request.request_id = identifier;
	request.priority = 1U;
	request.service = app->pending_service;
	request.argument = app->wire_argument;
	return (flipnet_protocol_build_request(&request));
}

bool	flipnet_request_try(t_flipnet_app *app)
{
	t_flipnet_transport	transport;
	uint32_t			identifier;

	flipnet_transport_refresh(app);
	transport = flipnet_transport_choose(app);
	if (transport == TRANSPORT_NONE)
		return (false);
	identifier = ++app->next_request;
	if (!flipnet_request_build(app, identifier))
		return (false);
	app->pending_request = identifier;
	app->pending_transport = transport;
	app->waiting_transport = false;
	if (flipnet_transport_send(app, transport))
		return (true);
	app->pending_request = 0U;
	app->pending_transport = TRANSPORT_NONE;
	app->waiting_transport = true;
	return (false);
}

void	flipnet_request_begin(t_flipnet_app *app)
{
	app->pending_request = 0U;
	app->pending_transport = TRANSPORT_NONE;
	app->waiting_transport = true;
	flipnet_flow_begin(app);
	flipnet_request_try(app);
}
