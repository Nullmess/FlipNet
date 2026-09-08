#include "flipnet_files.h"
#include "flipnet_core.h"

#include <storage/storage.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define FLIPNET_FS_PROTOCOL "FNFS2"
#define FLIPNET_FS_PATH_SIZE 256U
#define FLIPNET_FS_DATA_SIZE 512U
#define FLIPNET_FS_LIST_ITEMS 3U
#define FLIPNET_FS_LIST_SIZE 1024U
#define FLIPNET_FS_HEX_SIZE (FLIPNET_FS_LIST_SIZE * 2U + 1U)

typedef struct s_flipnet_fs_buffers
{
	char	hex[FLIPNET_FS_HEX_SIZE];
	char	line[FLIPNET_PROTOCOL_MAX_LINE];
} t_flipnet_fs_buffers;

static t_flipnet_fs_buffers	g_flipnet_fs_buffers;

typedef struct s_flipnet_fs_upload
{
	t_flipnet_app	*owner;
	Storage			*storage;
	File			*file;
	uint32_t		expected;
	uint32_t		offset;
	char			path[FLIPNET_FS_PATH_SIZE];
} t_flipnet_fs_upload;

static t_flipnet_fs_upload	g_flipnet_fs_upload;

void	flipnet_files_reset(t_flipnet_app *app)
{
	if (g_flipnet_fs_upload.owner
		&& app && g_flipnet_fs_upload.owner != app)
		return ;
	if (g_flipnet_fs_upload.file)
	{
		storage_file_close(g_flipnet_fs_upload.file);
		storage_file_free(g_flipnet_fs_upload.file);
	}
	if (g_flipnet_fs_upload.storage)
		furi_record_close(RECORD_STORAGE);
	memset(&g_flipnet_fs_upload, 0, sizeof(g_flipnet_fs_upload));
}

static char	*flipnet_fs_token(char **cursor)
{
	char	*token;
	char	*separator;

	if (!cursor || !*cursor)
		return (NULL);
	token = *cursor;
	separator = strchr(token, '|');
	if (separator)
	{
		*separator = '\0';
		*cursor = separator + 1U;
	}
	else
		*cursor = NULL;
	return (token);
}

static bool	flipnet_fs_crc_valid(char *text)
{
	char			*separator;
	char			*end;
	unsigned long	received;
	uint32_t		calculated;

	separator = strrchr(text, '|');
	if (!separator || strlen(separator + 1U) != 8U)
		return (false);
	received = strtoul(separator + 1U, &end, 16);
	if (*end)
		return (false);
	calculated = flipnet_protocol_crc32((const uint8_t *)text,
		(size_t)(separator - text));
	*separator = '\0';
	return ((uint32_t)received == calculated);
}

static bool	flipnet_fs_u32(const char *text, uint32_t *value)
{
	char			*end;
	unsigned long	parsed;

	if (!text || !*text || !value)
		return (false);
	parsed = strtoul(text, &end, 10);
	if (*end || parsed > UINT32_MAX)
		return (false);
	*value = (uint32_t)parsed;
	return (true);
}

static bool	flipnet_fs_path_segment_valid(const char *start, size_t length)
{
	if (length == 0U)
		return (false);
	if (length == 1U && start[0] == '.')
		return (false);
	if (length == 2U && start[0] == '.' && start[1] == '.')
		return (false);
	return (true);
}

static bool	flipnet_fs_path_valid(const char *path)
{
	const char	*segment;
	const char	*cursor;
	size_t		index;

	if (!path || !*path || strlen(path) >= FLIPNET_FS_PATH_SIZE)
		return (false);
	if (strncmp(path, "/ext", 4U) && strncmp(path, "/int", 4U))
		return (false);
	if ((path[4] != '\0' && path[4] != '/') || strstr(path, "//")
		|| strchr(path, '\\'))
		return (false);
	index = 0U;
	while (path[index])
	{
		if ((unsigned char)path[index] < 0x20U)
			return (false);
		index++;
	}
	if (path[4] == '\0')
		return (true);
	segment = path + 5;
	while (*segment)
	{
		cursor = strchr(segment, '/');
		if (!cursor)
		cursor = segment + strlen(segment);
		if (!flipnet_fs_path_segment_valid(segment, (size_t)(cursor - segment)))
			return (false);
		if (!*cursor)
			break ;
		segment = cursor + 1;
	}
	return (true);
}

static bool	flipnet_fs_path_writable(const char *path)
{
	return (flipnet_fs_path_valid(path) && !strncmp(path, "/ext/", 5U));
}

static bool	flipnet_fs_decode_text(const char *hex, char *output,
			size_t output_size)
{
	size_t	decoded;

	if (!hex || !output || output_size < 2U)
		return (false);
	if (!flipnet_protocol_hex_decode(hex, (uint8_t *)output,
			output_size - 1U, &decoded))
		return (false);
	output[decoded] = '\0';
	return (true);
}

static bool	flipnet_fs_transport_send(t_flipnet_app *app,
			const char *data, size_t size)
{
	t_flipnet_transport	transport;
	t_usb_payload		usb;
	t_ble_payload		ble;

	transport = flipnet_transport_choose(app);
	if (transport == TRANSPORT_USB)
	{
		usb.data = data;
		usb.length = size;
		return (flipnet_usb_send(app->usb, usb));
	}
	if (transport == TRANSPORT_BLE)
	{
		ble.data = data;
		ble.length = size;
		return (flipnet_ble_send(app->ble, ble));
	}
	return (false);
}

static bool	flipnet_fs_send(t_flipnet_app *app, uint32_t id,
			const char *status, const char *command, uint64_t value1,
			uint64_t value2, const uint8_t *data, size_t data_size)
{
	uint32_t	crc;
	int		length;
	int		crc_length;

	/* These buffers used to live on the app thread stack. A Files request
	 * could then nest ~10 KiB of local arrays and overflow the FAP stack,
	 * eventually surfacing as an unrelated furi_check failure. Files are
	 * processed serially on the ViewDispatcher thread, so one static
	 * workspace is sufficient and avoids allocation churn per 512-byte block. */
	if (data_size > FLIPNET_FS_LIST_SIZE
		|| !flipnet_protocol_hex_encode(data, data_size, g_flipnet_fs_buffers.hex,
			sizeof(g_flipnet_fs_buffers.hex)))
		return (false);
	length = snprintf(g_flipnet_fs_buffers.line, sizeof(g_flipnet_fs_buffers.line),
		FLIPNET_FS_PROTOCOL "|RES|%lu|%s|%s|%llu|%llu|%s",
		(unsigned long)id, status, command,
		(unsigned long long)value1, (unsigned long long)value2,
		g_flipnet_fs_buffers.hex);
	if (length < 0 || (size_t)length >= sizeof(g_flipnet_fs_buffers.line))
		return (false);
	crc = flipnet_protocol_crc32((const uint8_t *)g_flipnet_fs_buffers.line,
		(size_t)length);
	crc_length = snprintf(g_flipnet_fs_buffers.line + length,
		sizeof(g_flipnet_fs_buffers.line) - (size_t)length,
		"|%08lX\n", (unsigned long)crc);
	if (crc_length < 0
		|| (size_t)crc_length >= sizeof(g_flipnet_fs_buffers.line) - (size_t)length)
		return (false);
	return (flipnet_fs_transport_send(app, g_flipnet_fs_buffers.line,
		(size_t)length + (size_t)crc_length));
}

static bool	flipnet_fs_error(t_flipnet_app *app, uint32_t id,
			const char *command, const char *message)
{
	return (flipnet_fs_send(app, id, "ERR", command, 0U, 0U,
		(const uint8_t *)message, strlen(message)));
}

static bool	flipnet_fs_list(t_flipnet_app *app, uint32_t id,
			const char *path, uint32_t offset)
{
	Storage		*storage;
	File		*directory;
	FileInfo	info;
	char		name[FLIPNET_FS_PATH_SIZE];
	char		listing[FLIPNET_FS_LIST_SIZE];
	size_t		used;
	uint32_t	index;
	uint32_t	count;
	bool		read_ok;
	bool		has_more;
	int		written;

	if (!flipnet_fs_path_valid(path))
		return (flipnet_fs_error(app, id, "LIST", "Invalid path"));
	storage = furi_record_open(RECORD_STORAGE);
	directory = storage_file_alloc(storage);
	if (!directory)
	{
		furi_record_close(RECORD_STORAGE);
		return (flipnet_fs_error(app, id, "LIST", "Directory unavailable"));
	}
	if (!storage_dir_open(directory, path))
	{
		storage_dir_close(directory);
		storage_file_free(directory);
		furi_record_close(RECORD_STORAGE);
		return (flipnet_fs_error(app, id, "LIST", "Directory unavailable"));
	}
	index = 0U;
	while (index < offset
		&& storage_dir_read(directory, &info, name, sizeof(name)))
		index++;
	used = 0U;
	count = 0U;
	read_ok = false;
	has_more = false;
	while (count < FLIPNET_FS_LIST_ITEMS)
	{
		read_ok = storage_dir_read(directory, &info, name, sizeof(name));
		if (!read_ok)
			break ;
		written = snprintf(listing + used, sizeof(listing) - used,
			"%c\t%llu\t%s\n", file_info_is_dir(&info) ? 'D' : 'F',
			(unsigned long long)info.size, name);
		if (written < 0 || (size_t)written >= sizeof(listing) - used)
		{
			has_more = true;
			break ;
		}
		used += (size_t)written;
		count++;
		index++;
	}
	if (count == FLIPNET_FS_LIST_ITEMS)
		has_more = true;
	storage_dir_close(directory);
	storage_file_free(directory);
	furi_record_close(RECORD_STORAGE);
	return (flipnet_fs_send(app, id, "OK", "LIST", index,
		has_more ? 0U : 1U, (const uint8_t *)listing, used));
}

static bool	flipnet_fs_space(t_flipnet_app *app, uint32_t id)
{
	Storage	*storage;
	uint64_t	ext_total;
	uint64_t	ext_free;
	uint64_t	int_total;
	uint64_t	int_free;
	char		result[128];
	int		length;

	storage = furi_record_open(RECORD_STORAGE);
	ext_total = 0U;
	ext_free = 0U;
	int_total = 0U;
	int_free = 0U;
	storage_common_fs_info(storage, "/ext", &ext_total, &ext_free);
	storage_common_fs_info(storage, "/int", &int_total, &int_free);
	furi_record_close(RECORD_STORAGE);
	length = snprintf(result, sizeof(result), "%llu,%llu,%llu,%llu",
		(unsigned long long)ext_total, (unsigned long long)ext_free,
		(unsigned long long)int_total, (unsigned long long)int_free);
	if (length < 0 || (size_t)length >= sizeof(result))
		return (flipnet_fs_error(app, id, "SPACE", "Storage info unavailable"));
	return (flipnet_fs_send(app, id, "OK", "SPACE", 0U, 0U,
		(const uint8_t *)result, (size_t)length));
}

static bool	flipnet_fs_mkdir(t_flipnet_app *app, uint32_t id,
			const char *path)
{
	Storage	*storage;
	FS_Error	result;

	if (!flipnet_fs_path_writable(path))
		return (flipnet_fs_error(app, id, "MKDIR", "Internal storage is read-only"));
	storage = furi_record_open(RECORD_STORAGE);
	result = storage_common_mkdir(storage, path);
	furi_record_close(RECORD_STORAGE);
	if (result != FSE_OK && result != FSE_EXIST)
		return (flipnet_fs_error(app, id, "MKDIR", storage_error_get_desc(result)));
	return (flipnet_fs_send(app, id, "OK", "MKDIR", 0U, 0U, NULL, 0U));
}

static bool	flipnet_fs_delete(t_flipnet_app *app, uint32_t id,
			const char *path)
{
	Storage	*storage;
	bool	result;

	if (!flipnet_fs_path_writable(path))
		return (flipnet_fs_error(app, id, "DELETE", "Internal storage is read-only"));
	storage = furi_record_open(RECORD_STORAGE);
	result = storage_simply_remove_recursive(storage, path);
	furi_record_close(RECORD_STORAGE);
	if (!result)
		return (flipnet_fs_error(app, id, "DELETE", "Delete failed"));
	return (flipnet_fs_send(app, id, "OK", "DELETE", 0U, 0U, NULL, 0U));
}

static bool	flipnet_fs_rename(t_flipnet_app *app, uint32_t id,
			const char *path, const char *new_path)
{
	Storage	*storage;
	FileInfo	destination_info;
	FS_Error	result;

	if (!flipnet_fs_path_writable(path) || !flipnet_fs_path_writable(new_path))
		return (flipnet_fs_error(app, id, "RENAME", "Internal storage is read-only"));
	storage = furi_record_open(RECORD_STORAGE);
	if (storage_common_stat(storage, new_path, &destination_info) == FSE_OK)
	{
		furi_record_close(RECORD_STORAGE);
		return (flipnet_fs_error(app, id, "RENAME", "Destination already exists"));
	}
	result = storage_common_rename(storage, path, new_path);
	furi_record_close(RECORD_STORAGE);
	if (result != FSE_OK)
		return (flipnet_fs_error(app, id, "RENAME", storage_error_get_desc(result)));
	return (flipnet_fs_send(app, id, "OK", "RENAME", 0U, 0U, NULL, 0U));
}

static bool	flipnet_fs_read(t_flipnet_app *app, uint32_t id,
			const char *path, uint32_t offset)
{
	Storage		*storage;
	File		*file;
	uint8_t		data[FLIPNET_FS_DATA_SIZE];
	uint64_t	total;
	size_t		read_size;

	if (!flipnet_fs_path_valid(path))
		return (flipnet_fs_error(app, id, "READ", "Invalid path"));
	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	if (!file || !storage_file_open(file, path, FSAM_READ, FSOM_OPEN_EXISTING))
	{
		if (file)
			storage_file_free(file);
		furi_record_close(RECORD_STORAGE);
		return (flipnet_fs_error(app, id, "READ", "File unavailable"));
	}
	total = storage_file_size(file);
	if (total > UINT32_MAX || offset > total
		|| !storage_file_seek(file, offset, true))
	{
		storage_file_close(file);
		storage_file_free(file);
		furi_record_close(RECORD_STORAGE);
		return (flipnet_fs_error(app, id, "READ", "File too large or invalid offset"));
	}
	read_size = storage_file_read(file, data, sizeof(data));
	storage_file_close(file);
	storage_file_free(file);
	furi_record_close(RECORD_STORAGE);
	return (flipnet_fs_send(app, id, "OK", "READ", offset + read_size,
		total, data, read_size));
}

static bool	flipnet_fs_write_begin(t_flipnet_app *app, uint32_t id,
			const char *path, uint32_t total)
{
	Storage	*storage;
	File	*file;

	if (!flipnet_fs_path_writable(path))
		return (flipnet_fs_error(app, id, "WRITE_BEGIN",
				"Internal storage is read-only"));
	flipnet_files_reset(app);
	storage = furi_record_open(RECORD_STORAGE);
	file = storage_file_alloc(storage);
	if (!file || !storage_file_open(file, path, FSAM_WRITE, FSOM_CREATE_ALWAYS))
	{
		if (file)
			storage_file_free(file);
		furi_record_close(RECORD_STORAGE);
		return (flipnet_fs_error(app, id, "WRITE_BEGIN", "Cannot create file"));
	}
	g_flipnet_fs_upload.owner = app;
	g_flipnet_fs_upload.storage = storage;
	g_flipnet_fs_upload.file = file;
	g_flipnet_fs_upload.expected = total;
	g_flipnet_fs_upload.offset = 0U;
	flipnet_copy(g_flipnet_fs_upload.path, sizeof(g_flipnet_fs_upload.path), path);
	return (flipnet_fs_send(app, id, "OK", "WRITE_BEGIN", 0U, total, NULL, 0U));
}

static bool	flipnet_fs_write(t_flipnet_app *app, uint32_t id,
			const char *path, uint32_t offset, const uint8_t *data,
			size_t data_size)
{
	size_t	written;
	uint32_t	end_offset;

	if (!flipnet_fs_path_writable(path) || data_size > FLIPNET_FS_DATA_SIZE)
		return (flipnet_fs_error(app, id, "WRITE", "Invalid write request"));
	if (g_flipnet_fs_upload.owner != app || !g_flipnet_fs_upload.file
		|| strcmp(g_flipnet_fs_upload.path, path))
		return (flipnet_fs_error(app, id, "WRITE", "Upload session unavailable"));
	if (offset > g_flipnet_fs_upload.offset
		|| data_size > UINT32_MAX - offset)
		return (flipnet_fs_error(app, id, "WRITE", "Invalid upload offset"));
	if (!storage_file_seek(g_flipnet_fs_upload.file, offset, true))
		return (flipnet_fs_error(app, id, "WRITE", "Write seek failed"));
	written = storage_file_write(g_flipnet_fs_upload.file, data, data_size);
	if (written != data_size)
		return (flipnet_fs_error(app, id, "WRITE", "Write failed"));
	end_offset = offset + (uint32_t)written;
	if (end_offset > g_flipnet_fs_upload.offset)
		g_flipnet_fs_upload.offset = end_offset;
	return (flipnet_fs_send(app, id, "OK", "WRITE",
		g_flipnet_fs_upload.offset, g_flipnet_fs_upload.expected, NULL, 0U));
}

static bool	flipnet_fs_write_end(t_flipnet_app *app, uint32_t id,
			const char *path, uint32_t expected)
{
	uint64_t	size;
	bool		valid;

	if (!flipnet_fs_path_writable(path))
		return (flipnet_fs_error(app, id, "WRITE_END", "Invalid path"));
	if (g_flipnet_fs_upload.owner != app || !g_flipnet_fs_upload.file
		|| strcmp(g_flipnet_fs_upload.path, path))
		return (flipnet_fs_error(app, id, "WRITE_END", "Upload session unavailable"));
	valid = expected == g_flipnet_fs_upload.expected
		&& g_flipnet_fs_upload.offset == expected;
	storage_file_sync(g_flipnet_fs_upload.file);
	size = storage_file_size(g_flipnet_fs_upload.file);
	valid = valid && size == expected;
	flipnet_files_reset(app);
	if (!valid)
		return (flipnet_fs_error(app, id, "WRITE_END", "Uploaded size mismatch"));
	return (flipnet_fs_send(app, id, "OK", "WRITE_END", size, expected, NULL, 0U));
}

static bool	flipnet_fs_dispatch(t_flipnet_app *app, uint32_t id,
			const char *command, const char *path, const char *argument,
			uint32_t value, const uint8_t *data, size_t data_size)
{
	if (!strcmp(command, "LIST"))
		return (flipnet_fs_list(app, id, path, value));
	if (!strcmp(command, "SPACE"))
		return (flipnet_fs_space(app, id));
	if (!strcmp(command, "MKDIR"))
		return (flipnet_fs_mkdir(app, id, path));
	if (!strcmp(command, "DELETE"))
		return (flipnet_fs_delete(app, id, path));
	if (!strcmp(command, "RENAME"))
		return (flipnet_fs_rename(app, id, path, argument));
	if (!strcmp(command, "READ"))
		return (flipnet_fs_read(app, id, path, value));
	if (!strcmp(command, "WRITE_BEGIN"))
		return (flipnet_fs_write_begin(app, id, path, value));
	if (!strcmp(command, "WRITE"))
		return (flipnet_fs_write(app, id, path, value, data, data_size));
	if (!strcmp(command, "WRITE_END"))
		return (flipnet_fs_write_end(app, id, path, value));
	return (flipnet_fs_error(app, id, command, "Unknown file command"));
}

bool	flipnet_files_handle_line(t_flipnet_app *app, const char *line)
{
	char		*copy;
	char		*cursor;
	char		*version;
	char		*type;
	char		*id_text;
	char		*command;
	char		*path_hex;
	char		*argument_hex;
	char		*value_text;
	char		*data_hex;
	char		path[FLIPNET_FS_PATH_SIZE];
	char		argument[FLIPNET_FS_PATH_SIZE];
	uint8_t		data[FLIPNET_FS_DATA_SIZE];
	size_t		data_size;
	size_t		line_length;
	uint32_t	id;
	uint32_t	value;
	bool		handled;

	if (!app || !line || strncmp(line, FLIPNET_FS_PROTOCOL "|", 6U))
		return (false);
	line_length = strlen(line);
	if (line_length >= FLIPNET_PROTOCOL_MAX_LINE)
		return (true);
	/* Keep the maximum protocol line off the already-busy app stack. */
	copy = malloc(line_length + 1U);
	if (!copy)
		return (true);
	memcpy(copy, line, line_length + 1U);
	handled = true;
	if (!flipnet_fs_crc_valid(copy))
	{
		free(copy);
		return (handled);
	}
	cursor = copy;
	version = flipnet_fs_token(&cursor);
	type = flipnet_fs_token(&cursor);
	id_text = flipnet_fs_token(&cursor);
	command = flipnet_fs_token(&cursor);
	path_hex = flipnet_fs_token(&cursor);
	argument_hex = flipnet_fs_token(&cursor);
	value_text = flipnet_fs_token(&cursor);
	data_hex = flipnet_fs_token(&cursor);
	if (!version || !type || !id_text || !command || !path_hex
		|| !argument_hex || !value_text || !data_hex || cursor
		|| strcmp(version, FLIPNET_FS_PROTOCOL) || strcmp(type, "REQ")
		|| !flipnet_fs_u32(id_text, &id) || !flipnet_fs_u32(value_text, &value)
		|| !flipnet_fs_decode_text(path_hex, path, sizeof(path))
		|| !flipnet_fs_decode_text(argument_hex, argument, sizeof(argument))
		|| !flipnet_protocol_hex_decode(data_hex, data, sizeof(data), &data_size))
	{
		free(copy);
		return (handled);
	}
	flipnet_fs_dispatch(app, id, command, path, argument, value, data, data_size);
	free(copy);
	return (handled);
}
