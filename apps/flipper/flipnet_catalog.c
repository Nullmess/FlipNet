#include "flipnet_catalog.h"

#include <string.h>

bool	flipnet_slice_copy(char *output, size_t output_size,
			t_flipnet_slice input)
{
	if (!output || !output_size || !input.data || input.size >= output_size)
		return (false);
	memcpy(output, input.data, input.size);
	output[input.size] = '\0';
	return (true);
}

bool	flipnet_slice_copy_truncated(char *output, size_t output_size,
			t_flipnet_slice input)
{
	if (!output || !output_size || !input.data)
		return (false);
	if (input.size >= output_size)
		input.size = output_size - 1U;
	memcpy(output, input.data, input.size);
	output[input.size] = '\0';
	return (true);
}

bool	flipnet_slice_is_decimal(t_flipnet_slice value)
{
	size_t	index;

	if (!value.data || !value.size)
		return (false);
	index = 0U;
	while (index < value.size)
	{
		if (value.data[index] < '0' || value.data[index] > '9')
			return (false);
		index++;
	}
	return (true);
}

bool	flipnet_slice_is_upper_hex(t_flipnet_slice value)
{
	size_t	index;
	char	character;

	if (!value.data || !value.size)
		return (false);
	index = 0U;
	while (index < value.size)
	{
		character = value.data[index];
		if (!((character >= '0' && character <= '9')
				|| (character >= 'A' && character <= 'F')))
			return (false);
		index++;
	}
	return (true);
}

bool	flipnet_slice_is_identifier(t_flipnet_slice value)
{
	size_t	index;
	char	character;

	if (!value.data || !value.size)
		return (false);
	index = 0U;
	while (index < value.size)
	{
		character = value.data[index];
		if (!((character >= 'a' && character <= 'z')
				|| (character >= '0' && character <= '9')
				|| character == '_'))
			return (false);
		index++;
	}
	return (true);
}

