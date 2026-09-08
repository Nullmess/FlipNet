#ifndef FLIPNET_CAPABILITY_CATALOG_I_H
# define FLIPNET_CAPABILITY_CATALOG_I_H

# include "flipnet_capability_catalog.h"
# include "flipnet_catalog.h"

typedef struct s_flipnet_feature_data
{
	t_flipnet_slice	fields[18];
	char			id[FLIPNET_CAPABILITY_ID_SIZE];
	char			buffer[193];
	char			visible[2];
	char			next_service[FLIPNET_CAPABILITY_ID_SIZE];
} t_flipnet_feature_data;

typedef struct s_flipnet_app_data
{
	t_flipnet_slice		fields[12];
	t_flipnet_remote_app	*item;
	char				kind[2];
	char				mode[FLIPNET_CAPABILITY_MODE_SIZE];
	char				visible[2];
} t_flipnet_app_data;

typedef struct s_flipnet_page
{
	t_flipnet_capabilities	catalog;
	const char			*cursor;
	uint8_t				index;
} t_flipnet_page;

bool	flipnet_capability_id(const char *value, size_t length);
int		flipnet_capability_hex(char value);
bool	flipnet_capability_decode(char *output, size_t output_size,
			t_flipnet_slice input);
bool	flipnet_capability_add_service(t_flipnet_capabilities *catalog,
			const char *service);
bool	flipnet_capability_fields(t_flipnet_slice line,
			t_flipnet_slice *fields, size_t count);
char	flipnet_capability_app_mode(const char *mode);
bool	flipnet_feature_decode_first(t_flipnet_feature_data *data);
bool	flipnet_feature_decode_labels(t_flipnet_feature_data *data);
bool	flipnet_capability_page_start(
			const t_flipnet_capabilities *catalog, const char *payload,
			t_flipnet_page *page);
bool	flipnet_capability_feature(t_flipnet_capabilities *catalog,
			t_flipnet_slice line);
bool	flipnet_capability_app(t_flipnet_capabilities *catalog,
			t_flipnet_slice line);
unsigned int	flipnet_capability_decimal(const char *value);
bool	flipnet_capability_header(t_flipnet_capabilities *catalog,
			t_flipnet_slice line, uint8_t *page);
t_flipnet_catalog_result	flipnet_capability_v2(
			t_flipnet_capabilities *catalog, const char *payload);

#endif
