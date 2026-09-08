#include "flipnet_protocol.h"

#include <stdlib.h>
#include <string.h>

bool	flipnet_crc_valid(char *text);
bool	flipnet_status(const char *text, t_flipnet_response *response);
bool	flipnet_response_text(char **cursor, t_flipnet_response *response);
char	*flipnet_protocol_token(char **cursor);
void	flipnet_protocol_trim(char *text);
bool	flipnet_protocol_id(const char *text, uint32_t *result);

static bool	flipnet_parse_fields(char **cursor, char **fields)
{
	size_t	index;

	index = 0U;
	while (index < 5U)
	{
		fields[index] = flipnet_protocol_token(cursor);
		if (!fields[index])
			return (false);
		index++;
	}
	return (true);
}

static bool	flipnet_parse_v2(char *text, t_flipnet_response *response)
{
	char	*cursor;
	char	*fields[5];

	if (!flipnet_crc_valid(text))
		return (false);
	cursor = text;
	if (!flipnet_parse_fields(&cursor, fields)
		|| strcmp(fields[0], "FN2") || strcmp(fields[1], "RES")
		|| !flipnet_protocol_id(fields[2], &response->session_id)
		|| !flipnet_protocol_id(fields[3], &response->request_id)
		|| !flipnet_status(fields[4], response))
		return (false);
	return (flipnet_response_text(&cursor, response));
}

bool	flipnet_protocol_parse_response(const char *line,
			t_flipnet_response *response)
{
	char	*scratch;
	size_t	length;
	bool	valid;

	if (!line || !response)
		return (false);
	length = strlen(line);
	if (!length || length >= FLIPNET_PROTOCOL_MAX_LINE)
		return (false);
	scratch = malloc(length + 1U);
	if (!scratch)
		return (false);
	memcpy(scratch, line, length + 1U);
	flipnet_protocol_trim(scratch);
	valid = !strncmp(scratch, "FN2|", 4U)
		&& flipnet_parse_v2(scratch, response);
	free(scratch);
	return (valid);
}
