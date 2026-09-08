#include "flipnet_usb_i.h"

#include <stdlib.h>
#include <string.h>

static void	flipnet_usb_release(t_usb *instance)
{
	if (instance->send_mutex)
		furi_mutex_free(instance->send_mutex);
	if (instance->events)
		furi_event_flag_free(instance->events);
	if (instance->cli_vcp)
		furi_record_close(RECORD_CLI_VCP);
	free(instance);
}

static bool	flipnet_usb_start(t_usb *instance)
{
	if (!flipnet_usb_configure(instance))
		return (false);
	instance->worker = furi_thread_alloc_ex("FlipNetUsb",
			FLIPNET_USB_WORKER_STACK, flipnet_usb_worker, instance);
	if (!instance->worker)
	{
		flipnet_usb_restore(instance);
		return (false);
	}
	furi_thread_start(instance->worker);
	flipnet_usb_callbacks(instance);
	furi_hal_cdc_set_callbacks(FLIPNET_USB_IF_NUM,
		&instance->cdc_callbacks, instance);
	return (true);
}

t_usb	*flipnet_usb_alloc(t_usb_cbs callbacks)
{
	t_usb	*instance;

	instance = malloc(sizeof(t_usb));
	if (!instance)
		return (NULL);
	memset(instance, 0, sizeof(t_usb));
	instance->events = furi_event_flag_alloc();
	instance->send_mutex = furi_mutex_alloc(FuriMutexTypeNormal);
	instance->callbacks = callbacks;
	instance->cli_vcp = furi_record_open(RECORD_CLI_VCP);
	if (!instance->events || !instance->send_mutex
		|| !flipnet_usb_start(instance))
	{
		flipnet_usb_release(instance);
		return (NULL);
	}
	return (instance);
}

void	flipnet_usb_free(t_usb *instance)
{
	if (!instance)
		return ;
	instance->stopping = true;
	furi_hal_cdc_set_callbacks(FLIPNET_USB_IF_NUM, NULL, NULL);
	furi_event_flag_set(instance->events, USB_EVENT_STOP);
	furi_thread_join(instance->worker);
	furi_thread_free(instance->worker);
	flipnet_usb_restore(instance);
	flipnet_usb_release(instance);
}

bool	flipnet_usb_is_connected(t_usb *instance)
{
	if (!instance)
		return (false);
	return (instance->connected);
}
