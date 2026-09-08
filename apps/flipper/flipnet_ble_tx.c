#include "flipnet_ble_i.h"

static bool	flipnet_ble_wait(t_ble *instance)
{
	uint32_t	flags;

	flags = furi_event_flag_wait(instance->events,
			FLIPNET_BLE_TX_DONE | FLIPNET_BLE_DISCONNECTED,
			FuriFlagWaitAny | FuriFlagNoClear,
			furi_ms_to_ticks(FLIPNET_BLE_SEND_TIMEOUT_MS));
	if (flags & FLIPNET_BLE_DISCONNECTED)
		return (false);
	return ((flags & FLIPNET_BLE_TX_DONE) != 0U);
}

static bool	flipnet_ble_chunk(t_ble *instance,
			const char *data, uint16_t length)
{
	/* Disconnect is a sticky event. Clearing it here races a real GAP
	 * disconnect and can turn it into a TX timeout. Only TX_DONE belongs
	 * to the current fragment. */
	furi_event_flag_clear(instance->events, FLIPNET_BLE_TX_DONE);
	if (!ble_profile_serial_tx(instance->profile, (uint8_t *)data, length))
		return (false);
	return (flipnet_ble_wait(instance));
}

static bool	flipnet_ble_write(t_ble *instance,
			t_ble_payload payload)
{
	size_t			offset;
	uint16_t	chunk;

	offset = 0U;
	while (offset < payload.length)
	{
		chunk = (uint16_t)MIN((size_t)BLE_PROFILE_SERIAL_PACKET_SIZE_MAX,
				payload.length - offset);
		if (!flipnet_ble_chunk(instance, payload.data + offset, chunk))
			return (false);
		offset += chunk;
	}
	return (true);
}

bool	flipnet_ble_send(t_ble *instance, t_ble_payload payload)
{
	bool	success;

	if (!instance || !payload.data || payload.length == 0U)
		return (false);
	if (instance->shutting_down || !instance->connected || !instance->profile)
		return (false);
	if (furi_mutex_acquire(instance->send_mutex, FuriWaitForever)
		!= FuriStatusOk)
		return (false);
	/* Re-check after taking the mutex: the link can disappear while another
	 * sender owns it. */
	if (instance->shutting_down || !instance->connected || !instance->profile)
		success = false;
	else
		success = flipnet_ble_write(instance, payload);
	furi_mutex_release(instance->send_mutex);
	return (success);
}
