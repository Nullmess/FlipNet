#include "flipnet_capability_catalog_i.h"

#include <string.h>

bool	flipnet_capability_add_service(t_flipnet_capabilities *catalog,
			const char *service)
{
	size_t	used;
	size_t	length;

	length = strlen(service);
	if (!flipnet_capability_id(service, length))
		return (false);
	used = strlen(catalog->services);
	if (!used)
	{
		if (length + 3U > sizeof(catalog->services))
			return (false);
		catalog->services[0] = ',';
		memcpy(catalog->services + 1U, service, length);
		catalog->services[length + 1U] = ',';
		catalog->services[length + 2U] = '\0';
		return (true);
	}
	if (used + length + 2U > sizeof(catalog->services))
		return (false);
	memcpy(catalog->services + used, service, length);
	catalog->services[used + length] = ',';
	catalog->services[used + length + 1U] = '\0';
	return (true);
}

bool	flipnet_capability_fields(t_flipnet_slice line,
			t_flipnet_slice *fields, size_t count)
{
	const char	*cursor;
	const char	*end;
	size_t		index;

	cursor = line.data;
	end = line.data + line.size;
	index = 0U;
	while (index < count)
	{
		if (!flipnet_catalog_next_field(&cursor, end, &fields[index]))
			return (false);
		index++;
	}
	return (cursor == end);
}

char	flipnet_capability_app_mode(const char *mode)
{
	if (!strcmp(mode, "run"))
		return ('R');
	if (!strcmp(mode, "search"))
		return ('S');
	if (!strcmp(mode, "pick_input"))
		return ('P');
	if (!strcmp(mode, "translate_flow"))
		return ('T');
	if (!strcmp(mode, "input"))
		return ('I');
	return ('\0');
}

bool	flipnet_feature_decode_first(t_flipnet_feature_data *data)
{
	if (!flipnet_capability_decode(data->buffer, 2U, data->fields[0])
		|| strcmp(data->buffer, "F")
		|| !flipnet_capability_decode(data->id, sizeof(data->id),
			data->fields[1])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_DOMAIN_SIZE, data->fields[2])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_GROUP_SIZE, data->fields[3])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_KIND_SIZE, data->fields[4])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_CATEGORY_SIZE, data->fields[5]))
		return (false);
	return (true);
}

bool	flipnet_feature_decode_labels(t_flipnet_feature_data *data)
{
	if (!flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_MODE_SIZE, data->fields[6])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_LABEL_SIZE, data->fields[7])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_LABEL_SIZE, data->fields[8])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_PROMPT_SIZE, data->fields[9])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_PROMPT_SIZE, data->fields[10])
		|| !flipnet_capability_decode(data->buffer,
			FLIPNET_CAPABILITY_INITIAL_SIZE, data->fields[11]))
		return (false);
	return (true);
}
