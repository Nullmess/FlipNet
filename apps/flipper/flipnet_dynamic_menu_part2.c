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

bool flipnet_menu_parent_has(t_flipnet_app *app, const char *parent)
{
	size_t index;
	const t_flipnet_feature_definition *feature;

	if (!app || !parent)
		return (false);
	index = 0U;
	while (index < g_flipnet_features_count)
	{
		feature = &g_flipnet_features[index];
		if (feature->menu_visible
			&& !strcmp(feature->menu_parent, parent)
			&& flipnet_has_service(app, feature->id))
			return (true);
		index++;
	}
	return (false);
}

void flipnet_menu_build_parent(Submenu *menu, t_flipnet_app *app,
		const char *parent)
{
	unsigned int order;
	size_t index;
	const t_flipnet_feature_definition *feature;

	if (!menu || !app || !parent)
		return ;
	order = 0U;
	while (order <= 63U)
	{
		index = 0U;
		while (index < g_flipnet_features_count)
		{
			feature = &g_flipnet_features[index];
			if (feature->menu_visible
				&& feature->menu_order == order
				&& !strcmp(feature->menu_parent, parent)
				&& flipnet_has_service(app, feature->id))
				submenu_add_item(menu, flipnet_dynamic_label(app, feature),
					(uint32_t)index, flipnet_menu_feature_callback, app);
			index++;
		}
		order++;
	}
}
