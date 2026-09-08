#include "flipnet_capability_catalog_i.h"

#include <string.h>

static bool	flipnet_feature_decode_tail(t_flipnet_feature_data *data)
{
	size_t	index;

	index = 12U;
	while (index < 16U)
	{
		if (!flipnet_capability_decode(data->buffer, sizeof(data->buffer),
				data->fields[index]))
			return (false);
		index++;
	}
	if (!flipnet_capability_decode(data->visible, sizeof(data->visible),
			data->fields[16])
		|| !flipnet_capability_decode(data->next_service,
			sizeof(data->next_service), data->fields[17]))
		return (false);
	return (true);
}

bool	flipnet_capability_feature(t_flipnet_capabilities *catalog,
			t_flipnet_slice line)
{
	t_flipnet_feature_data	data;

	if (catalog->feature_count >= FLIPNET_CAPABILITY_FEATURE_ITEMS
		|| !flipnet_capability_fields(line, data.fields, 18U))
		return (false);
	if (!flipnet_feature_decode_first(&data)
		|| !flipnet_feature_decode_labels(&data)
		|| !flipnet_feature_decode_tail(&data))
		return (false);
	if (!flipnet_capability_id(data.id, strlen(data.id))
		|| (data.next_service[0]
			&& !flipnet_capability_id(data.next_service,
				strlen(data.next_service)))
		|| (strcmp(data.visible, "0") && strcmp(data.visible, "1"))
		|| !flipnet_capability_add_service(catalog, data.id))
		return (false);
	catalog->feature_count++;
	return (true);
}

static bool	flipnet_app_decode_first(t_flipnet_app_data *data)
{
	if (!flipnet_capability_decode(data->kind, sizeof(data->kind),
			data->fields[0])
		|| strcmp(data->kind, "A")
		|| !flipnet_capability_decode(data->item->id,
			sizeof(data->item->id), data->fields[1])
		|| !flipnet_capability_decode(data->item->service,
			sizeof(data->item->service), data->fields[2])
		|| !flipnet_capability_decode(data->mode, sizeof(data->mode),
			data->fields[3])
		|| !flipnet_capability_decode(data->item->title_en,
			sizeof(data->item->title_en), data->fields[4])
		|| !flipnet_capability_decode(data->item->title_fr,
			sizeof(data->item->title_fr), data->fields[5]))
		return (false);
	return (true);
}

static bool	flipnet_app_decode_tail(t_flipnet_app_data *data)
{
	if (!flipnet_capability_decode(data->item->prompt_en,
			sizeof(data->item->prompt_en), data->fields[6])
		|| !flipnet_capability_decode(data->item->prompt_fr,
			sizeof(data->item->prompt_fr), data->fields[7])
		|| !flipnet_capability_decode(data->item->initial,
			sizeof(data->item->initial), data->fields[8])
		|| !flipnet_capability_decode(data->item->origin,
			sizeof(data->item->origin), data->fields[9])
		|| !flipnet_capability_decode(data->item->extension_group,
			sizeof(data->item->extension_group), data->fields[10])
		|| !flipnet_capability_decode(data->visible,
			sizeof(data->visible), data->fields[11]))
		return (false);
	return (true);
}

bool	flipnet_capability_app(t_flipnet_capabilities *catalog,
			t_flipnet_slice line)
{
	t_flipnet_app_data	data;

	if (catalog->app_count >= FLIPNET_CAPABILITY_APP_ITEMS
		|| !flipnet_capability_fields(line, data.fields, 12U))
		return (false);
	data.item = &catalog->apps[catalog->app_count];
	if (!flipnet_app_decode_first(&data) || !flipnet_app_decode_tail(&data))
		return (false);
	data.item->mode = flipnet_capability_app_mode(data.mode);
	if (!flipnet_capability_id(data.item->id, strlen(data.item->id))
		|| !data.item->mode
		|| !flipnet_capability_id(data.item->service,
			strlen(data.item->service))
		|| strcmp(data.visible, "1"))
		return (false);
	catalog->app_count++;
	return (true);
}
