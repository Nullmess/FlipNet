#include "flipnet_error_protocol.h"

#include <ctype.h>
#include <string.h>

int	flipnet_error_hex(char value);

static bool	flipnet_error_escape(const char *input, size_t *index,
			char *value)
{
	int	high;
	int	low;

	if (!input[*index + 1U] || !input[*index + 2U])
		return (false);
	high = flipnet_error_hex(input[*index + 1U]);
	low = flipnet_error_hex(input[*index + 2U]);
	if (high < 0 || low < 0)
		return (false);
	*value = (char)((high << 4) | low);
	*index += 3U;
	return (true);
}

bool	flipnet_error_code_valid(const char *value)
{
	size_t	index;

	if (!value || !*value || strlen(value) >= FLIPNET_ERROR_CODE_SIZE)
		return (false);
	index = 0U;
	while (value[index])
	{
		if (!(value[index] == '_' || isdigit((unsigned char)value[index])
				|| (value[index] >= 'A' && value[index] <= 'Z')))
			return (false);
		index++;
	}
	return (true);
}

bool	flipnet_error_id_valid(const char *value, size_t limit,
			bool empty_allowed)
{
	size_t	index;

	if (!value || (!empty_allowed && !*value) || strlen(value) > limit)
		return (false);
	index = 0U;
	while (value[index])
	{
		if (!(value[index] == '_' || isdigit((unsigned char)value[index])
				|| (value[index] >= 'a' && value[index] <= 'z')))
			return (false);
		index++;
	}
	return (true);
}

int	flipnet_error_hex(char value)
{
	if (value >= '0' && value <= '9')
		return (value - '0');
	if (value >= 'A' && value <= 'F')
		return (value - 'A' + 10);
	if (value >= 'a' && value <= 'f')
		return (value - 'a' + 10);
	return (-1);
}

bool	flipnet_error_decode(char *output, size_t output_size,
			const char *input)
{
	size_t	read_index;
	size_t	write_index;

	if (!output || !output_size || !input)
		return (false);
	read_index = 0U;
	write_index = 0U;
	while (input[read_index] && write_index + 1U < output_size)
	{
		if (input[read_index] == '%')
		{
			if (!flipnet_error_escape(input, &read_index,
					&output[write_index]))
				return (false);
		}
		else
			output[write_index] = input[read_index++];
		write_index++;
	}
	if (input[read_index])
		return (false);
	output[write_index] = '\0';
	return (true);
}
