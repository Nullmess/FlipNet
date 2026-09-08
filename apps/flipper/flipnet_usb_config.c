#include "flipnet_usb_i.h"

#include <furi_hal_usb.h>

bool	flipnet_usb_configure(t_usb *instance)
{

	furi_hal_usb_unlock();
	cli_vcp_enable(instance->cli_vcp);
	if (!furi_hal_usb_set_config(&usb_cdc_dual, NULL))
	{
		furi_hal_usb_set_config(&usb_cdc_single, NULL);
		return (false);
	}
	return (true);
}

void	flipnet_usb_restore(t_usb *instance)
{
	furi_hal_usb_unlock();
	furi_hal_usb_set_config(&usb_cdc_single, NULL);
	cli_vcp_enable(instance->cli_vcp);
}
