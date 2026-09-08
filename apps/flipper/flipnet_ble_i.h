#ifndef FLIPNET_BLE_I_H
# define FLIPNET_BLE_I_H

# include "flipnet_ble.h"
# include "flipnet_protocol.h"

# include <bt/bt_service/bt.h>
# include <furi.h>
# include <profiles/serial_profile.h>

# define FLIPNET_BLE_SEND_TIMEOUT_MS 4000U
# define FLIPNET_BLE_SHUTDOWN_TIMEOUT_MS 3000U
# define FLIPNET_BLE_LINK_FRAME "FN2|LINK|UP"
# define FLIPNET_BLE_LINK_TIMEOUT_MS 12000U

typedef enum e_flipnet_ble_event
{
	FLIPNET_BLE_TX_DONE = (1U << 0),
	FLIPNET_BLE_DISCONNECTED = (1U << 1)
}	t_ble_event;

struct s_flipnet_ble
{
	Bt						*bt;
	FuriHalBleProfileBase	*profile;
	FuriEventFlag			*events;
	FuriMutex				*send_mutex;
	bool					connected;
	bool					shutting_down;
	bool					gap_connected;
	bool					link_seen;
	bool					advertising;
	uint32_t				last_link_tick;
	char					receive_line[FLIPNET_PROTOCOL_MAX_LINE];
	size_t					receive_length;
	t_ble_cbs				callbacks;
};

void		flipnet_ble_notify(t_ble *instance);

void		flipnet_ble_mark_active(t_ble *instance);

void		flipnet_ble_register(t_ble *instance);

void		flipnet_ble_status(BtStatus status, void *context);

uint16_t	flipnet_ble_serial(SerialServiceEvent event, void *context);

#endif
