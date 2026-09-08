#include "flipnet_error_protocol.h"

#include <string.h>

bool	flipnet_error_code_valid(const char *value);
bool	flipnet_error_id_valid(const char *value, size_t limit,
			bool empty_allowed);
int		flipnet_error_hex(char value);
bool	flipnet_error_decode(char *output, size_t output_size,
			const char *input);

bool	flipnet_error_fields(char *payload, char **fields)
{
	size_t	index;
	char	*cursor;

	index = 0U;
	cursor = payload;
	fields[index++] = cursor;
	while (*cursor && index < 7U)
	{
		if (*cursor == '|')
		{
			*cursor = '\0';
			fields[index++] = cursor + 1U;
		}
		cursor++;
	}
	return (index == 7U && !strchr(fields[6], '|'));
}

static bool	flipnet_error_metadata(char **fields,
			t_flipnet_error_result *result)
{
	if (strcmp(fields[0], "FNERR1")
		|| !flipnet_error_code_valid(fields[1])
		|| !flipnet_error_id_valid(fields[2], 24U, false)
		|| !flipnet_error_id_valid(fields[3], 32U, true)
		|| (strcmp(fields[4], "0") && strcmp(fields[4], "1")))
		return (false);
	memcpy(result->code, fields[1], strlen(fields[1]) + 1U);
	result->recoverable = !strcmp(fields[4], "1");
	return (true);
}

bool	flipnet_error_protocol_apply(char *payload, size_t payload_size,
			bool french, t_flipnet_error_result *result)
{
	char	*fields[7];
	char	*message;

	if (!payload || !payload_size || !result)
		return (false);
	if (strncmp(payload, "FNERR1|", 7U)
		|| !flipnet_error_fields(payload, fields)
		|| !flipnet_error_metadata(fields, result))
		return (false);
	message = fields[5];
	if (french)
		message = fields[6];
	return (flipnet_error_decode(payload, payload_size, message));
}
