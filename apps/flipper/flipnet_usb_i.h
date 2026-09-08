#ifndef FLIPNET_USB_I_H
# define FLIPNET_USB_I_H

# include "flipnet_protocol.h"
# include "flipnet_usb.h"

# include <cli/cli_vcp.h>
# include <furi.h>
# include <furi_hal_usb_cdc.h>

# define FLIPNET_USB_IF_NUM 1U
# define FLIPNET_USB_SEND_TIMEOUT_MS 3000U
# define FLIPNET_USB_LINK_POLL_MS 1000U
# define FLIPNET_USB_LINK_TIMEOUT_MS 6000U
# define FLIPNET_USB_LINK_FRAME "FN2|LINK|UP"
# define FLIPNET_USB_WORKER_STACK 2048U

typedef enum e_usb_event
{
	USB_EVENT_STOP = (1U << 0),
	USB_EVENT_RX = (1U << 1),
	USB_EVENT_TX_DONE = (1U << 2),
	USB_EVENT_CONNECTED = (1U << 3),
	USB_EVENT_DISCONNECTED = (1U << 4)
}	t_usb_event;

struct s_flipnet_usb
{
	CliVcp					*cli_vcp;
	FuriThread				*worker;
	FuriEventFlag			*events;
	FuriMutex				*send_mutex;
	volatile bool			connected;
	volatile bool			stopping;
	volatile bool			link_seen;
	uint32_t				last_link_tick;
	char					receive_line[FLIPNET_PROTOCOL_MAX_LINE];
	size_t					receive_length;
	t_usb_cbs				callbacks;
	CdcCallbacks			cdc_callbacks;
};

void		flipnet_usb_callbacks(t_usb *instance);

bool		flipnet_usb_configure(t_usb *instance);

void		flipnet_usb_restore(t_usb *instance);

void		flipnet_usb_notify(t_usb *instance);

void		flipnet_usb_link_up(t_usb *instance);

void		flipnet_usb_rx(t_usb *instance, const uint8_t *data, size_t size);

int32_t		flipnet_usb_worker(void *context);

#endif
