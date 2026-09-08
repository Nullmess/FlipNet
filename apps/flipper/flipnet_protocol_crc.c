#include "flipnet_protocol.h"

uint32_t	flipnet_protocol_crc32(const uint8_t *data, size_t size)
{
	uint32_t	crc;
	size_t		index;
	uint8_t		bit;

	crc = 0xFFFFFFFFU;
	index = 0U;
	while (index < size)
	{
		crc ^= data[index++];
		bit = 0U;
		while (bit++ < 8U)
			crc = (crc >> 1U) ^ (0xEDB88320U & (0U - (crc & 1U)));
	}
	return (~crc);
}
