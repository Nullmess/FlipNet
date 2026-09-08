#include "flipnet_usb_i.h"

static void	flipnet_usb_tx_done(void *context)
{
	t_usb	*instance;

	instance = context;
	if (!instance->stopping)
		furi_event_flag_set(instance->events, USB_EVENT_TX_DONE);
}

static void	flipnet_usb_rx_ready(void *context)
{
	t_usb	*instance;

	instance = context;
	if (!instance->stopping)
		furi_event_flag_set(instance->events, USB_EVENT_RX);
}

static void	flipnet_usb_state(void *context, CdcState state)
{
	t_usb	*instance;

	instance = context;
	if (instance->stopping)
		return ;
	if (state == CdcStateDisconnected)
	{
		instance->link_seen = false;
		instance->connected = false;
		furi_event_flag_set(instance->events, USB_EVENT_DISCONNECTED);
	}
}

static void	flipnet_usb_control(void *context, CdcCtrlLine lines)
{
	t_usb			*instance;
	bool			connected;
	uint32_t		event;

	instance = context;
	if (instance->stopping)
		return ;
	connected = (lines & CdcCtrlLineDTR) != 0;
	if (!connected && instance->link_seen)
		return ;
	instance->connected = connected;
	event = USB_EVENT_DISCONNECTED;
	if (connected)
		event = USB_EVENT_CONNECTED;
	furi_event_flag_set(instance->events, event);
}

void	flipnet_usb_callbacks(t_usb *instance)
{
	instance->cdc_callbacks.tx_ep_callback = flipnet_usb_tx_done;
	instance->cdc_callbacks.rx_ep_callback = flipnet_usb_rx_ready;
	instance->cdc_callbacks.state_callback = flipnet_usb_state;
	instance->cdc_callbacks.ctrl_line_callback = flipnet_usb_control;
	instance->cdc_callbacks.config_callback = NULL;
}
