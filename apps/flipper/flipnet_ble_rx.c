#include "flipnet_ble_i.h"

#include <stdint.h>
#include <string.h>

static void	flipnet_ble_line(t_ble *instance)
{
	instance->receive_line[instance->receive_length] = '\0';
	if (!strcmp(instance->receive_line, FLIPNET_BLE_LINK_FRAME))
	{
		flipnet_ble_mark_active(instance);
		instance->receive_length = 0U;
		return ;
	}
	flipnet_ble_mark_active(instance);
	if (instance->callbacks.line)
		instance->callbacks.line(instance->receive_line,
			instance->callbacks.context);
	instance->receive_length = 0U;
}

static void	flipnet_ble_byte(t_ble *instance, char value)
{
	if (value == '\n' || value == '\r')
	{
		if (instance->receive_length > 0U)
			flipnet_ble_line(instance);
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

static void	flipnet_ble_receive(t_ble *instance,
			const uint8_t *data, size_t size)
{
	size_t	index;

	index = 0U;
	while (index < size)
	{
		flipnet_ble_byte(instance, (char)data[index]);
		index++;
	}
}

uint16_t	flipnet_ble_serial(SerialServiceEvent event, void *context)
{
	t_ble			*instance;
	size_t			free_space;

	instance = context;
	if (!instance || instance->shutting_down)
		return (0U);
	if (event.event == SerialServiceEventTypeDataReceived)
	{
		flipnet_ble_receive(instance, event.data.buffer, event.data.size);
		free_space = sizeof(instance->receive_line) - 1U;
		free_space -= instance->receive_length;
		return ((uint16_t)MIN(free_space, (size_t)UINT16_MAX));
	}
	if (event.event == SerialServiceEventTypeDataSent)
		furi_event_flag_set(instance->events, FLIPNET_BLE_TX_DONE);
	return (0U);
}
