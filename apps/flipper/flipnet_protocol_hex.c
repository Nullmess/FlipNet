#include "flipnet_protocol.h"

#include <string.h>

static int	flipnet_hex_value(char value)
{
	if (value >= '0' && value <= '9')
		return (value - '0');
	if (value >= 'a' && value <= 'f')
		return (value - 'a' + 10);
	if (value >= 'A' && value <= 'F')
		return (value - 'A' + 10);
	return (-1);
}

bool	flipnet_protocol_hex_encode(const uint8_t *input, size_t input_size,
			char *output, size_t output_size)
{
	const char	*digits;
	size_t		index;

	digits = "0123456789ABCDEF";
	if (!output || (input_size > 0U && !input))
		return (false);
	if (output_size < input_size * 2U + 1U)
		return (false);
	index = 0U;
	while (index < input_size)
	{
		output[index * 2U] = digits[(input[index] >> 4U) & 0x0FU];
		output[index * 2U + 1U] = digits[input[index] & 0x0FU];
		index++;
	}
	output[input_size * 2U] = '\0';
	return (true);
}

bool	flipnet_protocol_hex_decode(const char *input, uint8_t *output,
			size_t output_size, size_t *decoded_size)
{
	size_t	index;
	size_t	result_size;
	int		high;
	int		low;

	if (!input || !output || !decoded_size)
		return (false);
	result_size = strlen(input) / 2U;
	if (strlen(input) % 2U != 0U || result_size > output_size)
		return (false);
	index = 0U;
	while (index < result_size)
	{
		high = flipnet_hex_value(input[index * 2U]);
		low = flipnet_hex_value(input[index * 2U + 1U]);
		if (high < 0 || low < 0)
			return (false);
		output[index] = (uint8_t)((high << 4) | low);
		index++;
	}
	*decoded_size = result_size;
	return (true);
}
