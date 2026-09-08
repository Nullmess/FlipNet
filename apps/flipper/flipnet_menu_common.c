#include "flipnet_core.h"
#include "flipnet_menu.h"
#include "flipnet_spec.h"

Submenu	*flipnet_submenu(const char *header)
{
	Submenu	*menu;

	menu = submenu_alloc();
	if (menu)
		submenu_set_header(menu, header);
	return (menu);
}

void	flipnet_menu_service(Submenu *menu, t_flipnet_app *app,
			t_flipnet_menu_service item)
{
	if (menu && flipnet_has_service(app, item.service))
		submenu_add_item(menu, item.label, item.index,
			flipnet_menu_callback, app);
}

void	flipnet_menu_group(Submenu *menu, t_flipnet_app *app,
			t_flipnet_menu_group item)
{
	if (menu && flipnet_has_any_service(app, item.services, item.count))
		submenu_add_item(menu, item.label, item.index,
			flipnet_menu_callback, app);
}

t_input_spec	flipnet_spec(t_flipnet_view parent, const char *service,
			const char *prompt, const char *initial)
{
	t_input_spec	spec;

	spec.parent = parent;
	spec.service = service;
	spec.prompt = prompt;
	spec.initial = initial;
	return (spec);
}
