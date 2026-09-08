#include "flipnet_features_generated.h"
#include "flipnet_files.h"
#include "flipnet_core.h"
#include "flipnet_ui.h"

#include <stdlib.h>
#include <string.h>

bool	flipnet_request_try(t_flipnet_app *app);
void	flipnet_response_process(t_flipnet_app *app,
			t_flipnet_response *response);

void	flipnet_event_timeout(t_flipnet_app *app);
uint16_t	flipnet_request_timeout_frames(t_flipnet_app *app);
void	flipnet_event_animation(t_flipnet_app *app);
void	flipnet_event_transport(t_flipnet_app *app);
void	flipnet_event_line(t_flipnet_app *app);

void	flipnet_event_timeout(t_flipnet_app *app)
{
	flipnet_flow_timeout(app);
}

uint16_t	flipnet_request_timeout_frames(t_flipnet_app *app)
{
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_CAPABILITIES)
		&& app->pending_transport == TRANSPORT_BLE)
		return (FLIPNET_CAPABILITIES_BLE_TIMEOUT_FRAMES);
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_HOST_DISCOVERY))
		return (FLIPNET_HOST_DISCOVERY_TIMEOUT_FRAMES);
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_PORT_SCAN))
		return (FLIPNET_PORT_SCAN_TIMEOUT_FRAMES);
	if (!strncmp(app->pending_service, "web_", 4U))
		return (FLIPNET_WEB_TIMEOUT_FRAMES);
	if (!strcmp(app->pending_service, FLIPNET_FEATURE_APP_RUN))
		return (FLIPNET_APP_TIMEOUT_FRAMES);
	return (FLIPNET_TIMEOUT_FRAMES);
}

void	flipnet_event_animation(t_flipnet_app *app)
{
	if (!app->animation)
		return ;
	app->animation_frame++;
	if (app->waiting_transport
		&& app->animation_frame <= FLIPNET_CONNECT_FRAMES)
		flipnet_request_try(app);
	if (app->animation_frame >= flipnet_request_timeout_frames(app))
		flipnet_event_timeout(app);
	else if (!app->request_silent)
		flipnet_loading_tick(app);
}

void	flipnet_event_transport(t_flipnet_app *app)
{
	t_flipnet_transport	current;

	flipnet_transport_refresh(app);
	current = flipnet_transport_choose(app);
	if (current != TRANSPORT_NONE && app->capabilities->loaded
		&& app->catalog_transport != TRANSPORT_NONE
		&& current != app->catalog_transport)
		flipnet_capabilities_invalidate(app);
	flipnet_overview_transport(app);
	if (app->pending_transport == TRANSPORT_USB && !app->usb_connected)
		app->pending_request = 0U;
	if (app->pending_transport == TRANSPORT_BLE && !app->ble_connected)
		app->pending_request = 0U;
	if (!app->capabilities->loaded && !app->capabilities_attempted
		&& !app->animation && current != TRANSPORT_NONE)
	{
		flipnet_capabilities_refresh_background(app);
		return ;
	}
	if (!app->pending_request && app->animation)
	{
		app->pending_transport = TRANSPORT_NONE;
		app->waiting_transport = true;
		flipnet_request_try(app);
	}
}

void	flipnet_event_line(t_flipnet_app *app)
{
	t_flipnet_response	*response;
	char				*line;

	line = NULL;
	if (furi_message_queue_get(app->lines, &line, 0U) != FuriStatusOk)
		return ;
	if (flipnet_files_handle_line(app, line))
	{
		free(line);
		if (furi_message_queue_get_count(app->lines) > 0U)
			view_dispatcher_send_custom_event(app->dispatcher, EVENT_LINE);
		return ;
	}
	response = malloc(sizeof(t_flipnet_response));
	if (response && flipnet_protocol_parse_response(line, response))
		flipnet_response_process(app, response);
	free(response);
	free(line);
	if (furi_message_queue_get_count(app->lines) > 0U)
		view_dispatcher_send_custom_event(app->dispatcher, EVENT_LINE);
}
