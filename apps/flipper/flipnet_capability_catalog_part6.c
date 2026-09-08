#include "flipnet_capability_catalog_i.h"

#include <string.h>

bool	flipnet_capability_catalog_has(const t_flipnet_capabilities *catalog,
			const char *service)
{
	char	needle[36];
	size_t	length;

	if (!catalog || !catalog->loaded || !service)
		return (false);
	length = strlen(service);
	if (!flipnet_capability_id(service, length)
		|| length + 3U > sizeof(needle))
		return (false);
	needle[0] = ',';
	memcpy(needle + 1U, service, length);
	needle[length + 1U] = ',';
	needle[length + 2U] = '\0';
	return (strstr(catalog->services, needle) != NULL);
}
