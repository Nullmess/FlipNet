#include "flipnet_i18n.h"

#include <storage/storage.h>

#define FLIPNET_LANGUAGE_PATH APP_DATA_PATH("language.txt")

void	flipnet_language_choose(t_flipnet_app *app, uint32_t index)
{
	t_flipnet_language	language;

	if (!app || index > 1U)
		return ;
	language = LANGUAGE_EN;
	if (index != 0U)
		language = LANGUAGE_FR;
	if (!flipnet_language_save(app, language))
		return ;
	app->restart_requested = true;
	view_dispatcher_stop(app->dispatcher);
}
