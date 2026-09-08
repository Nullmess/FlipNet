#include "flipnet_capability_catalog_i.h"

#include <string.h>

static bool	flipnet_capability_escape(t_flipnet_slice input,
			size_t *index, char *value)
{
	int	high;
	int	low;

	if (*index + 2U >= input.size)
		return (false);
	high = flipnet_capability_hex(input.data[*index + 1U]);
	low = flipnet_capability_hex(input.data[*index + 2U]);
	if (high < 0 || low < 0)
		return (false);
	*value = (char)((high << 4) | low);
	*index += 3U;
	return (true);
}

bool	flipnet_capability_id(const char *value, size_t length)
{
	size_t	index;

	if (!value || length == 0U || length > 32U)
		return (false);
	index = 0U;
	while (index < length)
	{
		if (!(value[index] >= 'a' && value[index] <= 'z')
			&& !(value[index] >= '0' && value[index] <= '9')
			&& value[index] != '_')
			return (false);
		index++;
	}
	return (true);
}

int	flipnet_capability_hex(char value)
{
	if (value >= '0' && value <= '9')
		return (value - '0');
	if (value >= 'A' && value <= 'F')
		return (value - 'A' + 10);
	if (value >= 'a' && value <= 'f')
		return (value - 'a' + 10);
	return (-1);
}

bool	flipnet_capability_decode(char *output, size_t output_size,
			t_flipnet_slice input)
{
	size_t	read_index;
	size_t	write_index;

	if (!output || !output_size || !input.data)
		return (false);
	read_index = 0U;
	write_index = 0U;
	while (read_index < input.size && write_index + 1U < output_size)
	{
		if (input.data[read_index] == '%')
		{
			if (!flipnet_capability_escape(input, &read_index,
					&output[write_index]))
				return (false);
		}
		else
			output[write_index] = input.data[read_index++];
		write_index++;
	}
	if (read_index != input.size)
		return (false);
	output[write_index] = '\0';
	return (true);
}

void	flipnet_capability_catalog_init(t_flipnet_capabilities *catalog)
{
	if (!catalog)
		return ;
	memset(catalog, 0, sizeof(*catalog));
}
