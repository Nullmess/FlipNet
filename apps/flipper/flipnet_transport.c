#include "flipnet_core.h"
#include "flipnet_i18n.h"

#include <storage/storage.h>

#define FLIPNET_TRANSPORT_PATH APP_DATA_PATH("transport.txt")

void	flipnet_transport_save(t_flipnet_app *app);
t_flipnet_transport	flipnet_transport_resolve(t_flipnet_app *app);

const char	*flipnet_transport_setting_label(t_flipnet_app *app)
{
	if (app->mode == MODE_USB)
		return (flipnet_tr(app, "Transport: USB", "Transport : USB"));
	if (app->mode == MODE_BLE)
		return (flipnet_tr(app, "Transport: BLE", "Transport : BLE"));
	return (flipnet_tr(app, "Transport: Auto", "Transport : Auto"));
}

void	flipnet_transport_load(t_flipnet_app *app)
{
	Storage	*storage;
	File	*file;
	char	value;

	if (!app)
		return ;
	app->mode = MODE_USB;
	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	if (file && storage_file_open(file, FLIPNET_TRANSPORT_PATH,
			FSAM_READ, FSOM_OPEN_EXISTING)
		&& storage_file_read(file, &value, 1U) == 1U)
	{
		if (value == 'b')
			app->mode = MODE_BLE;
		else if (value == 'a')
			app->mode = MODE_AUTO;
	}
	if (file)
	{
		storage_file_close(file);
		storage_file_free(file);
	}
	furi_record_close(RECORD_STORAGE);
}

void	flipnet_transport_save(t_flipnet_app *app)
{
	Storage	*storage;
	File	*file;
	char	value;

	value = 'u';
	if (app->mode == MODE_BLE)
		value = 'b';
	else if (app->mode == MODE_AUTO)
		value = 'a';
	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	if (file && storage_file_open(file, FLIPNET_TRANSPORT_PATH,
			FSAM_WRITE, FSOM_CREATE_ALWAYS))
		storage_file_write(file, &value, 1U);
	if (file)
	{
		storage_file_close(file);
		storage_file_free(file);
	}
	furi_record_close(RECORD_STORAGE);
}

t_flipnet_transport	flipnet_transport_resolve(t_flipnet_app *app)
{
	if (app->mode == MODE_USB)
	{
		if (app->usb_connected)
			return (TRANSPORT_USB);
		return (TRANSPORT_NONE);
	}
	if (app->mode == MODE_BLE)
	{
		if (app->ble_connected)
			return (TRANSPORT_BLE);
		return (TRANSPORT_NONE);
	}
	if (app->usb_connected)
		return (TRANSPORT_USB);
	if (app->ble_connected)
		return (TRANSPORT_BLE);
	return (TRANSPORT_NONE);
}

void	flipnet_transport_refresh(t_flipnet_app *app)
{
	if (!app)
		return ;
	app->usb_connected = flipnet_usb_is_connected(app->usb);
	app->ble_connected = flipnet_ble_is_connected(app->ble);
	app->active_transport = flipnet_transport_resolve(app);
}
