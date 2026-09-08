#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_ui.h"

#include <string.h>

static void	flipnet_response_invalid(t_flipnet_app *app,
			const char *title, const char *english, const char *french)
{
	flipnet_flow_result(app, title, flipnet_tr(app, english, french),
		CARD_FAILURE);
}

static bool	flipnet_response_weather(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_WEATHER_SEARCH))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR
		&& !flipnet_weather_show(app, response->body))
		flipnet_response_invalid(app, flipnet_tr(app, "Weather", "Meteo"),
			"Invalid city list", "Liste de villes invalide");
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

static bool	flipnet_response_wiki(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_WIKI_SEARCH))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR
		&& !flipnet_wikipedia_show(app, response->body))
		flipnet_response_invalid(app, "Wikipedia", "Invalid article list",
			"Liste d articles invalide");
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

static bool	flipnet_response_web(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (strcmp(app->pending_service, FLIPNET_FEATURE_WEB_SEARCH))
		return (false);
	if (response->status != FLIPNET_RESPONSE_ERROR
		&& !flipnet_web_show(app, response->body))
		flipnet_response_invalid(app, "Web", "Invalid result list",
			"Liste de resultats invalide");
	return (response->status != FLIPNET_RESPONSE_ERROR);
}

bool	flipnet_response_content(t_flipnet_app *app,
			t_flipnet_response *response)
{
	if (flipnet_response_weather(app, response))
		return (true);
	if (flipnet_response_wiki(app, response))
		return (true);
	return (flipnet_response_web(app, response));
}
