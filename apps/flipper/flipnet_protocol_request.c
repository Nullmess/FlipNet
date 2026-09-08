#include "flipnet_protocol.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static bool	flipnet_service_char(char value)
{
	return ((value >= 'a' && value <= 'z')
		|| (value >= '0' && value <= '9') || value == '_');
}

bool	flipnet_service_valid(const char *service)
{
	size_t	i;
	size_t	length;

	if (!service || !service[0])
		return (false);
	length = strlen(service);
	if (length > FLIPNET_PROTOCOL_MAX_SERVICE)
		return (false);
	i = 0U;
	while (i < length)
		if (!flipnet_service_char(service[i++]))
			return (false);
	return (true);
}

bool	flipnet_request_crc(t_flipnet_request *request, size_t length)
{
	uint32_t	crc;
	int		written;

	crc = flipnet_protocol_crc32((const uint8_t *)request->output, length);
	written = snprintf(request->output + length,
			request->output_size - length, "|%08lX\n", (unsigned long)crc);
	return (written > 0 && (size_t)written < request->output_size - length);
}
