#include "flipnet_capability_catalog_i.h"

#include <string.h>

static bool	flipnet_capability_same_page(
			const t_flipnet_capabilities *left,
			const t_flipnet_capabilities *right)
{
	if (strcmp(left->platform, right->platform)
		|| strcmp(left->revision, right->revision)
		|| left->total_pages != right->total_pages)
		return (false);
	return (true);
}

static bool	flipnet_capability_page_lines(t_flipnet_page *page)
{
	t_flipnet_slice	line;

	while (flipnet_catalog_next_line(&page->cursor, &line))
	{
		if (!line.size)
			continue ;
		if (line.data[0] == 'F')
		{
			if (!flipnet_capability_feature(&page->catalog, line))
				return (false);
		}
		else if (line.data[0] == 'A')
		{
			if (!flipnet_capability_app(&page->catalog, line))
				return (false);
		}
		else
			return (false);
	}
	return (true);
}

static t_flipnet_catalog_result	flipnet_capability_page_finish(
			t_flipnet_capabilities *catalog, t_flipnet_page *page)
{
	page->catalog.next_page = page->index + 1U;
	if (page->catalog.next_page >= page->catalog.total_pages)
	{
		page->catalog.loaded = true;
		page->catalog.loading = false;
	}
	*catalog = page->catalog;
	if (catalog->loaded)
		return (FLIPNET_CATALOG_COMPLETE);
	return (FLIPNET_CATALOG_MORE);
}

t_flipnet_catalog_result	flipnet_capability_v2(
			t_flipnet_capabilities *catalog, const char *payload)
{
	t_flipnet_page	page;

	if (!flipnet_capability_page_start(catalog, payload, &page))
		return (FLIPNET_CATALOG_INVALID);
	if (page.index && !flipnet_capability_same_page(&page.catalog, catalog))
		return (FLIPNET_CATALOG_INVALID);
	if (!flipnet_capability_page_lines(&page))
		return (FLIPNET_CATALOG_INVALID);
	return (flipnet_capability_page_finish(catalog, &page));
}

t_flipnet_catalog_result	flipnet_capability_catalog_parse(
			t_flipnet_capabilities *catalog, const char *payload)
{
	if (!catalog || !payload || strncmp(payload, "FNCAP2\n", 7U))
		return (FLIPNET_CATALOG_INVALID);
	return (flipnet_capability_v2(catalog, payload));
}
