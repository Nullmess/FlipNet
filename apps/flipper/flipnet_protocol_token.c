#include "flipnet_protocol.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

char	*flipnet_protocol_token(char **cursor)
{
	char	*token;
	char	*delimiter;

	if (!cursor || !*cursor)
		return (NULL);
	token = *cursor;
	delimiter = strchr(token, '|');
	if (!delimiter)
		*cursor = NULL;
	else
	{
		*delimiter = '\0';
		*cursor = delimiter + 1;
	}
	return (token);
}

bool	flipnet_protocol_id(const char *text, uint32_t *result)
{
	char			*end;
	unsigned long	value;
	size_t			index;

	if (!text || !text[0] || !result)
		return (false);
	index = 0U;
	while (text[index])
	{
		if (text[index] < '0' || text[index] > '9')
			return (false);
		index++;
	}
	errno = 0;
	end = NULL;
	value = strtoul(text, &end, 10);
	if (errno == ERANGE || !end || *end || value > UINT32_MAX)
		return (false);
	*result = (uint32_t)value;
	return (true);
}

void	flipnet_protocol_trim(char *text)
{
	size_t	length;

	length = strlen(text);
	while (length > 0U && (text[length - 1U] == '\n'
			|| text[length - 1U] == '\r'))
	{
		length--;
		text[length] = '\0';
	}
}
