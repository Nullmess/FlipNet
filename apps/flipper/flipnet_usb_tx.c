#include "flipnet_usb_i.h"

static bool	flipnet_usb_wait(t_usb *instance)
{
	uint32_t	flags;

	flags = furi_event_flag_wait(instance->events,
			USB_EVENT_TX_DONE | USB_EVENT_DISCONNECTED,
			FuriFlagWaitAny | FuriFlagNoClear,
			furi_ms_to_ticks(FLIPNET_USB_SEND_TIMEOUT_MS));
	if (flags & USB_EVENT_DISCONNECTED)
		return (false);
	return ((flags & USB_EVENT_TX_DONE) != 0U);
}

static bool	flipnet_usb_chunk(t_usb *instance,
			const char *data, uint16_t length)
{
	furi_event_flag_clear(instance->events,
		USB_EVENT_TX_DONE | USB_EVENT_DISCONNECTED);
	furi_hal_cdc_send(FLIPNET_USB_IF_NUM, (uint8_t *)data, length);
	return (flipnet_usb_wait(instance));
}

static bool	flipnet_usb_write(t_usb *instance,
			t_usb_payload payload)
{
	size_t			offset;
	uint16_t		chunk;

	offset = 0U;
	while (offset < payload.length)
	{
		chunk = (uint16_t)MIN((size_t)CDC_DATA_SZ, payload.length - offset);
		if (!flipnet_usb_chunk(instance, payload.data + offset, chunk))
			return (false);
		offset += chunk;
	}
	if (payload.length % CDC_DATA_SZ == 0U)
		return (flipnet_usb_chunk(instance, NULL, 0U));
	return (true);
}

bool	flipnet_usb_send(t_usb *instance,
			t_usb_payload payload)
{
	bool	success;

	if (!instance || !payload.data || payload.length == 0U)
		return (false);
	if (!instance->connected)
		return (false);
	if (furi_mutex_acquire(instance->send_mutex, FuriWaitForever)
		!= FuriStatusOk)
		return (false);
	success = flipnet_usb_write(instance, payload);
	furi_mutex_release(instance->send_mutex);
	return (success);
}
