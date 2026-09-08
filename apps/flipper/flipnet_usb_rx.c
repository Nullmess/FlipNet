#include "flipnet_usb_i.h"

#include <string.h>

static void	flipnet_usb_line(t_usb *instance)
{
	instance->receive_line[instance->receive_length] = '\0';
	if (!strcmp(instance->receive_line, FLIPNET_USB_LINK_FRAME))
		flipnet_usb_link_up(instance);
	else if (instance->callbacks.line)
		instance->callbacks.line(instance->receive_line,
			instance->callbacks.context);
	instance->receive_length = 0U;
}

static void	flipnet_usb_byte(t_usb *instance, char value)
{
	if (value == '\n' || value == '\r')
	{
		if (instance->receive_length > 0U)
			flipnet_usb_line(instance);
		return ;
	}
	if (instance->receive_length < sizeof(instance->receive_line) - 1U)
	{
		instance->receive_line[instance->receive_length] = value;
		instance->receive_length++;
		return ;
	}
	instance->receive_length = 0U;
}

void	flipnet_usb_rx(t_usb *instance,
			const uint8_t *data, size_t size)
{
	size_t	index;

	index = 0U;
	while (index < size)
	{
		flipnet_usb_byte(instance, (char)data[index]);
		index++;
	}
}

void	flipnet_usb_notify(t_usb *instance)
{
	if (!instance->callbacks.status)
		return ;
	instance->callbacks.status(instance->connected,
		instance->callbacks.context);
}
