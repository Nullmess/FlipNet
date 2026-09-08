#include "flipnet_protocol.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

bool	flipnet_service_valid(const char *service);
bool	flipnet_request_crc(t_flipnet_request *request, size_t length);

static char	*flipnet_request_encode(t_flipnet_request *request)
{
	char	*encoded;
	size_t	argument_size;
	size_t	encoded_size;

	if (!request->argument)
		request->argument = "";
	argument_size = strlen(request->argument);
	if (argument_size > FLIPNET_PROTOCOL_MAX_ARGUMENT)
		return (NULL);
	encoded_size = argument_size * 2U + 1U;
	encoded = malloc(encoded_size);
	if (!encoded)
		return (NULL);
	if (!flipnet_protocol_hex_encode((const uint8_t *)request->argument,
			argument_size, encoded, encoded_size))
	{
		free(encoded);
		return (NULL);
	}
	return (encoded);
}

static int	flipnet_request_write(t_flipnet_request *request,
			const char *encoded)
{
	return (snprintf(request->output, request->output_size,
			"FN2|REQ|%lu|%lu|%u|%s|%s",
			(unsigned long)request->session_id,
			(unsigned long)request->request_id,
			(unsigned int)request->priority, request->service, encoded));
}

bool	flipnet_protocol_build_request(t_flipnet_request *request)
{
	char	*encoded;
	int		written;

	if (!request || !request->output || !request->output_size
		|| !flipnet_service_valid(request->service))
		return (false);
	encoded = flipnet_request_encode(request);
	if (!encoded)
		return (false);
	written = flipnet_request_write(request, encoded);
	free(encoded);
	if (written <= 0 || (size_t)written >= request->output_size)
		return (false);
	return (flipnet_request_crc(request, (size_t)written));
}
