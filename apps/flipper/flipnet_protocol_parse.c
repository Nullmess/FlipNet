#include "flipnet_protocol.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

char	*flipnet_protocol_token(char **cursor);
bool	flipnet_protocol_id(const char *text, uint32_t *result);
void	flipnet_protocol_trim(char *text);

bool	flipnet_crc_valid(char *text)
{
	char		*separator;
	char		*end;
	unsigned long	received;
	uint32_t	calculated;

	separator = strrchr(text, '|');
	if (!separator || strlen(separator + 1U) != 8U)
		return (false);
	received = strtoul(separator + 1U, &end, 16);
	if (*end)
		return (false);
	calculated = flipnet_protocol_crc32((const uint8_t *)text,
		(size_t)(separator - text));
	*separator = '\0';
	return ((uint32_t)received == calculated);
}

bool	flipnet_status(const char *text, t_flipnet_response *response)
{
	if (!strcmp(text, "OK"))
		response->status = FLIPNET_RESPONSE_OK;
	else if (!strcmp(text, "ERR"))
		response->status = FLIPNET_RESPONSE_ERROR;
	else
		return (false);
	return (true);
}

bool	flipnet_response_text(char **cursor, t_flipnet_response *response)
{
	char	*title;
	char	*body;
	size_t	title_size;
	size_t	body_size;

	title = flipnet_protocol_token(cursor);
	body = flipnet_protocol_token(cursor);
	if (!title || !body || *cursor)
		return (false);
	if (!flipnet_protocol_hex_decode(title, (uint8_t *)response->title,
			FLIPNET_PROTOCOL_MAX_TITLE, &title_size)
		|| !flipnet_protocol_hex_decode(body, (uint8_t *)response->body,
			FLIPNET_PROTOCOL_MAX_BODY, &body_size))
		return (false);
	response->title[title_size] = '\0';
	response->body[body_size] = '\0';
	return (true);
}
