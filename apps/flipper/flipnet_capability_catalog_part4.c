#include "flipnet_capability_catalog_i.h"

#include <string.h>

unsigned int	flipnet_capability_decimal(const char *value)
{
	unsigned int	result;

	result = 0U;
	while (*value)
	{
		result = result * 10U + (unsigned int)(*value - '0');
		value++;
	}
	return (result);
}

bool	flipnet_capability_header(t_flipnet_capabilities *catalog,
			t_flipnet_slice line, uint8_t *page)
{
	t_flipnet_slice	fields[4];
	char			page_text[4];
	char			total_text[4];
	unsigned int	page_value;
	unsigned int	total_value;
	if (!flipnet_capability_fields(line, fields, 4U)
		|| !flipnet_capability_decode(catalog->platform,
			sizeof(catalog->platform), fields[0])
		|| !flipnet_capability_decode(page_text, sizeof(page_text), fields[1])
		|| !flipnet_capability_decode(total_text, sizeof(total_text), fields[2])
		|| !flipnet_capability_decode(catalog->revision,
			sizeof(catalog->revision), fields[3]))
		return (false);
	if (!flipnet_slice_is_decimal((t_flipnet_slice){page_text,
			strlen(page_text)})
		|| !flipnet_slice_is_decimal((t_flipnet_slice){total_text,
			strlen(total_text)}))
		return (false);
	page_value = flipnet_capability_decimal(page_text);
	total_value = flipnet_capability_decimal(total_text);
	if (!total_value || total_value > 255U || page_value >= total_value)
		return (false);
	*page = (uint8_t)page_value;
	catalog->total_pages = (uint8_t)total_value;
	return (true);
}

bool	flipnet_capability_page_start(
			const t_flipnet_capabilities *catalog, const char *payload,
			t_flipnet_page *page)
{
	t_flipnet_slice	line;

	page->catalog = *catalog;
	page->cursor = payload + 7U;
	if (!flipnet_catalog_next_line(&page->cursor, &line)
		|| !flipnet_capability_header(&page->catalog, line, &page->index))
		return (false);
	if (page->index == 0U)
	{
		flipnet_capability_catalog_init(&page->catalog);
		if (!flipnet_capability_header(&page->catalog, line, &page->index))
			return (false);
		page->catalog.loading = true;
	}
	else if (!page->catalog.loading || page->index != catalog->next_page)
		return (false);
	return (true);
}
