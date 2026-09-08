#include "flipnet_core.h"
#include "flipnet_menu.h"

void	flipnet_menu_operations_action(t_flipnet_app *app, uint32_t index)
{
	if (index == FLIPNET_MENU_AUTOMATION_MONITORING)
		flipnet_switch(app, VIEW_MONITORS);
	else if (index == FLIPNET_MENU_AUTOMATION_ACTIONS)
		flipnet_switch(app, VIEW_ACTIONS);
}
