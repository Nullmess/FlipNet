#ifndef FLIPNET_I18N_H
# define FLIPNET_I18N_H

# include "flipnet_app.h"

const char	*flipnet_tr(const t_flipnet_app *app, const char *english,
				const char *french);

const char	*flipnet_language_code(const t_flipnet_app *app);

const char	*flipnet_language_setting_label(const t_flipnet_app *app);

void		flipnet_language_load(t_flipnet_app *app);

bool		flipnet_language_save(t_flipnet_app *app,
			t_flipnet_language language);

void		flipnet_language_choose(t_flipnet_app *app, uint32_t index);

#endif
