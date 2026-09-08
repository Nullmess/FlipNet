#include "flipnet_ble_i.h"

#include <furi_hal_bt.h>
#include <stdlib.h>
#include <string.h>

static void	flipnet_ble_release(t_ble *instance)
{
	if (instance->send_mutex)
		furi_mutex_free(instance->send_mutex);
	if (instance->events)
		furi_event_flag_free(instance->events);
	if (instance->bt)
		furi_record_close(RECORD_BT);
	free(instance);
}

static bool	flipnet_ble_start(t_ble *instance)
{
	instance->profile = bt_profile_start(instance->bt,
			ble_profile_serial, NULL);
	if (!instance->profile)
		return (false);
	bt_set_status_changed_callback(instance->bt, flipnet_ble_status, instance);
	flipnet_ble_register(instance);
	furi_hal_bt_start_advertising();
	instance->advertising = furi_hal_bt_is_active();
	flipnet_ble_notify(instance);
	return (true);
}

t_ble	*flipnet_ble_alloc(t_ble_cbs callbacks)
{
	t_ble	*instance;

	if (!furi_hal_bt_is_alive() || !furi_hal_bt_is_gatt_gap_supported())
		return (NULL);
	instance = malloc(sizeof(t_ble));
	if (!instance)
		return (NULL);
	memset(instance, 0, sizeof(t_ble));
	instance->bt = furi_record_open(RECORD_BT);
	instance->events = furi_event_flag_alloc();
	instance->send_mutex = furi_mutex_alloc(FuriMutexTypeNormal);
	instance->callbacks = callbacks;
	if (!instance->events || !instance->send_mutex
		|| !flipnet_ble_start(instance))
	{
		flipnet_ble_release(instance);
		return (NULL);
	}
	return (instance);
}

void	flipnet_ble_free(t_ble *instance)
{
	uint32_t	flags;

	if (!instance)
		return ;
	instance->shutting_down = true;
	/* Stop serial callbacks first so no RX/TX callback can enqueue work while
	 * the application is being torn down. Keep the BT status callback alive
	 * just long enough to observe the asynchronous GAP disconnect. */
	if (instance->profile)
	{
		ble_profile_serial_set_event_callback(instance->profile, 0U, NULL, NULL);
		ble_profile_serial_set_rpc_active(instance->profile, false);
	}
	if (instance->gap_connected || instance->connected)
	{
		furi_event_flag_clear(instance->events, FLIPNET_BLE_DISCONNECTED);
		bt_disconnect(instance->bt);
		flags = furi_event_flag_wait(instance->events, FLIPNET_BLE_DISCONNECTED,
			FuriFlagWaitAny | FuriFlagNoClear,
			furi_ms_to_ticks(FLIPNET_BLE_SHUTDOWN_TIMEOUT_MS));
		UNUSED(flags);
	}
	bt_set_status_changed_callback(instance->bt, NULL, NULL);
	if (instance->profile)
	{
		bt_profile_restore_default(instance->bt);
		instance->profile = NULL;
	}
	flipnet_ble_release(instance);
}

bool	flipnet_ble_is_connected(t_ble *instance)
{
	uint32_t	elapsed;

	if (!instance)
		return (false);
	if (instance->gap_connected)
		return (true);
	if (!instance->link_seen)
		return (false);
	elapsed = furi_get_tick() - instance->last_link_tick;
	if (elapsed <= furi_ms_to_ticks(FLIPNET_BLE_LINK_TIMEOUT_MS))
		return (true);
	instance->connected = false;
	instance->link_seen = false;
	return (false);
}
