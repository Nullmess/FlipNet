#include "flipnet_usb_i.h"

static void	flipnet_usb_drain(t_usb *instance, uint8_t *buffer)
{
	int32_t	length;

	length = furi_hal_cdc_receive(FLIPNET_USB_IF_NUM, buffer, CDC_DATA_SZ);
	while (length > 0)
	{
		flipnet_usb_rx(instance, buffer, (size_t)length);
		length = furi_hal_cdc_receive(FLIPNET_USB_IF_NUM,
				buffer, CDC_DATA_SZ);
	}
}

void	flipnet_usb_link_up(t_usb *instance)
{
	bool	was_connected;

	was_connected = instance->connected;
	instance->link_seen = true;
	instance->last_link_tick = furi_get_tick();
	instance->connected = true;
	if (!was_connected)
		furi_event_flag_set(instance->events, USB_EVENT_CONNECTED);
}

static void	flipnet_usb_link_timeout(t_usb *instance)
{
	uint32_t	elapsed;

	if (!instance->link_seen || !instance->connected)
		return ;
	elapsed = furi_get_tick() - instance->last_link_tick;
	if (elapsed <= furi_ms_to_ticks(FLIPNET_USB_LINK_TIMEOUT_MS))
		return ;
	instance->link_seen = false;
	instance->connected = false;
	instance->receive_length = 0U;
	flipnet_usb_notify(instance);
}

static void	flipnet_usb_link_event(t_usb *instance)
{
	if (!instance->connected)
		instance->receive_length = 0U;
	flipnet_usb_notify(instance);
}

int32_t	flipnet_usb_worker(void *context)
{
	t_usb			*instance;
	uint8_t			buffer[CDC_DATA_SZ];
	uint32_t		flags;

	instance = context;
	while (true)
	{
		flags = furi_event_flag_wait(instance->events, USB_EVENT_STOP
				| USB_EVENT_RX | USB_EVENT_CONNECTED | USB_EVENT_DISCONNECTED,
				FuriFlagWaitAny,
				furi_ms_to_ticks(FLIPNET_USB_LINK_POLL_MS));
		if (flags & USB_EVENT_STOP)
			break ;
		if (flags & USB_EVENT_RX)
			flipnet_usb_drain(instance, buffer);
		if (flags & (USB_EVENT_CONNECTED | USB_EVENT_DISCONNECTED))
			flipnet_usb_link_event(instance);
		flipnet_usb_link_timeout(instance);
	}
	return (0);
}
