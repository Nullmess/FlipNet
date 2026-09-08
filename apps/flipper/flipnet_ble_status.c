#include "flipnet_ble_i.h"

#include <furi_hal_bt.h>

static bool	flipnet_ble_link_recent(t_ble *instance)
{
	uint32_t	elapsed;

	if (!instance->link_seen)
		return (false);
	elapsed = furi_get_tick() - instance->last_link_tick;
	return (elapsed <= furi_ms_to_ticks(FLIPNET_BLE_LINK_TIMEOUT_MS));
}

void	flipnet_ble_notify(t_ble *instance)
{
	if (!instance->callbacks.status)
		return ;
	instance->callbacks.status(instance->connected, instance->advertising,
		instance->callbacks.context);
}

void	flipnet_ble_mark_active(t_ble *instance)
{
	bool	changed;

	changed = !instance->connected;
	instance->link_seen = true;
	instance->last_link_tick = furi_get_tick();
	instance->connected = true;
	if (changed)
		flipnet_ble_notify(instance);
}

void	flipnet_ble_register(t_ble *instance)
{
	uint16_t	buffer_size;

	if (!instance->profile)
		return ;
	buffer_size = (uint16_t)MIN(sizeof(instance->receive_line) - 1U,
			(size_t)UINT16_MAX);
	ble_profile_serial_set_event_callback(instance->profile, buffer_size,
		flipnet_ble_serial, instance);
	ble_profile_serial_set_rpc_active(instance->profile, false);
}

void	flipnet_ble_status(BtStatus status, void *context)
{
	t_ble	*instance;

	instance = context;
	if (!instance)
		return ;
	instance->gap_connected = (status == BtStatusConnected);
	instance->advertising = (status == BtStatusAdvertising);
	if (instance->shutting_down)
	{
		if (!instance->gap_connected)
		{
			instance->connected = false;
			instance->link_seen = false;
			furi_event_flag_set(instance->events, FLIPNET_BLE_DISCONNECTED);
		}
		return ;
	}
	if (instance->gap_connected)
	{
		/* A disconnect flag is sticky for the lifetime of one connection and
		 * is cleared only when a new GAP connection is established. */
		furi_event_flag_clear(instance->events, FLIPNET_BLE_DISCONNECTED);
		instance->connected = true;
		flipnet_ble_register(instance);
	}
	else if (!flipnet_ble_link_recent(instance))
		instance->connected = false;
	if (!instance->connected)
		furi_event_flag_set(instance->events, FLIPNET_BLE_DISCONNECTED);
	flipnet_ble_notify(instance);
}
