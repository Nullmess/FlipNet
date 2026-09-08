#include "flipnet_i18n.h"

#include <storage/storage.h>

#define FLIPNET_LANGUAGE_PATH APP_DATA_PATH("language.txt")

const char	*flipnet_tr(const t_flipnet_app *app, const char *english,
				const char *french)
{
	if (app && app->language == LANGUAGE_FR)
		return (french);
	return (english);
}

const char	*flipnet_language_setting_label(const t_flipnet_app *app)
{
	if (app && app->language == LANGUAGE_FR)
		return ("Langue : Francais");
	return ("Language: English");
}

const char	*flipnet_language_code(const t_flipnet_app *app)
{
	if (app && app->language == LANGUAGE_FR)
		return ("fr");
	return ("en");
}

void	flipnet_language_load(t_flipnet_app *app)
{
	Storage	*storage;
	File	*file;
	char	value;

	if (!app)
		return ;
	app->language = LANGUAGE_UNSET;
	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	if (file && storage_file_open(file, FLIPNET_LANGUAGE_PATH,
			FSAM_READ, FSOM_OPEN_EXISTING)
		&& storage_file_read(file, &value, 1U) == 1U)
	{
		if (value == 'f')
			app->language = LANGUAGE_FR;
		else if (value == 'e')
			app->language = LANGUAGE_EN;
	}
	if (file)
	{
		storage_file_close(file);
		storage_file_free(file);
	}
	furi_record_close(RECORD_STORAGE);
}

bool	flipnet_language_save(t_flipnet_app *app, t_flipnet_language language)
{
	Storage	*storage;
	File	*file;
	char	value;
	bool	saved;

	if (!app || (language != LANGUAGE_EN && language != LANGUAGE_FR))
		return (false);
	value = 'e';
	if (language == LANGUAGE_FR)
		value = 'f';
	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	saved = false;
	if (file && storage_file_open(file, FLIPNET_LANGUAGE_PATH,
			FSAM_WRITE, FSOM_CREATE_ALWAYS))
		saved = storage_file_write(file, &value, 1U) == 1U;
	if (file)
	{
		storage_file_close(file);
		storage_file_free(file);
	}
	furi_record_close(RECORD_STORAGE);
	if (saved)
		app->language = language;
	return (saved);
}
