#include "flipnet_features_generated.h"
#include "flipnet_app.h"

#include <string.h>

static bool	flipnet_route_local_service(const char *service)
{
	if (!strncmp(service, "host_", 5U))
		return (true);
	if (!strncmp(service, "monitor", 7U))
		return (true);
	if (!strncmp(service, "alert", 5U))
		return (true);
	if (!strncmp(service, "favorite", 8U))
		return (true);
	if (!strncmp(service, FLIPNET_FEATURE_HISTORY, 7U))
		return (true);
	if (!strncmp(service, "action_", 7U))
		return (true);
	if (!strncmp(service, "playbook_", 9U))
		return (true);
	return (false);
}

t_flipnet_route	flipnet_route(const char *service)
{
	if (!service || !service[0])
		return (ROUTE_LOCAL);
	if (!strcmp(service, FLIPNET_FEATURE_CAPABILITIES)
		|| !strcmp(service, FLIPNET_FEATURE_DASHBOARD)
		|| !strcmp(service, FLIPNET_FEATURE_MONITORS))
		return (ROUTE_LOCAL);
	if (flipnet_route_local_service(service))
		return (ROUTE_LOCAL);
	if (strstr(service, "status")
		|| !strcmp(service, FLIPNET_FEATURE_NETWORK_GUARD))
		return (ROUTE_LOCAL);
	return (ROUTE_WAN);
}
