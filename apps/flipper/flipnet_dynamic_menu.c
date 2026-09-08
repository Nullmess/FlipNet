#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_menu.h"
#include "flipnet_spec.h"
#include "flipnet_ui.h"

#include <string.h>

const char *flipnet_dynamic_label(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature);
const char *flipnet_dynamic_prompt(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature);
bool flipnet_dynamic_native(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature);
void flipnet_dynamic_open(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature);

const char *flipnet_dynamic_label(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature)
{
	const char *english;
	const char *french;

	english = feature->label_en;
	if (feature->menu_label_en[0])
		english = feature->menu_label_en;
	french = feature->label_fr;
	if (feature->menu_label_fr[0])
		french = feature->menu_label_fr;
	return (flipnet_tr(app, english, french));
}

const char *flipnet_dynamic_prompt(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature)
{
	return (flipnet_tr(app, feature->prompt_en, feature->prompt_fr));
}

bool flipnet_dynamic_native(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature)
{
	if (!strcmp(feature->menu_native, "feeds"))
	{
		flipnet_switch(app, VIEW_FEEDS);
		return (true);
	}
	if (!strcmp(feature->menu_native, "apps"))
	{
		if (!flipnet_apps_open_cached(app))
			flipnet_capabilities_refresh_apps(app);
		return (true);
	}
	return (false);
}

void flipnet_dynamic_open(t_flipnet_app *app,
		const t_flipnet_feature_definition *feature)
{
	if (flipnet_dynamic_native(app, feature))
		return ;
	if (!strcmp(feature->ui_mode, "direct")
		|| !strcmp(feature->ui_mode, "confirm")
		|| !strcmp(feature->ui_mode, "article")
		|| !strcmp(feature->ui_mode, "feed"))
	{
		flipnet_request_direct(app, app->current_view,
			feature->id, feature->menu_argument);
		return ;
	}
	if (!strcmp(feature->ui_mode, "text")
		|| !strcmp(feature->ui_mode, "text_then_choices"))
	{
		flipnet_input_open(app, flipnet_spec(app->current_view,
				feature->id, flipnet_dynamic_prompt(app, feature),
				feature->initial));
	}
}

void flipnet_menu_feature_callback(void *context, uint32_t index)
{
	t_flipnet_app *app;
	const t_flipnet_feature_definition *feature;

	app = context;
	if (!app || index >= g_flipnet_features_count)
		return ;
	feature = &g_flipnet_features[index];
	if (!feature->menu_visible || !flipnet_has_service(app, feature->id))
		return ;
	flipnet_dynamic_open(app, feature);
}
