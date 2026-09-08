#include "flipnet_core.h"

#include <stdlib.h>
#include <string.h>

static bool	flipnet_overview_value(const char *body, const char *key,
			uint16_t *value)
{
	const char		*found;
	char			*end;
	unsigned long	number;

	found = strstr(body, key);
	if (!found)
		return (false);
	found += strlen(key);
	end = NULL;
	number = strtoul(found, &end, 10);
	if (end == found || number > UINT16_MAX)
		return (false);
	*value = (uint16_t)number;
	return (true);
}

bool	flipnet_overview_update(t_flipnet_app *app, const char *body)
{
	t_overview_model	*model;
	bool				valid;

	flipnet_transport_refresh(app);
	model = view_get_model(app->overview);
	model->transport = (uint8_t)flipnet_transport_choose(app);
	model->language = (uint8_t)app->language;
	valid = flipnet_overview_value(body, "Monitors=", &model->monitors);
	valid &= flipnet_overview_value(body, "OK=", &model->monitor_ok);
	valid &= flipnet_overview_value(body, "Error=", &model->monitor_error);
	valid &= flipnet_overview_value(body, "Alerts=", &model->alerts);
	valid &= flipnet_overview_value(body, "Targets=", &model->targets);
	valid &= flipnet_overview_value(body, "Favorites=", &model->favorites);
	valid &= flipnet_overview_value(body, "History=", &model->history);
	valid &= flipnet_overview_value(body, "Devices=", &model->devices);
	valid &= flipnet_overview_value(body, "New=", &model->new_devices);
	valid &= flipnet_overview_value(body, "Missing=", &model->missing_devices);
	valid &= flipnet_overview_value(body, "Changed=", &model->changed_devices);
	model->loading = false;
	model->failed = !valid;
	model->valid = valid;
	view_commit_model(app->overview, true);
	return (valid);
}
