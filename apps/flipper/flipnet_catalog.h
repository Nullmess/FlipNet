#ifndef FLIPNET_CATALOG_H
# define FLIPNET_CATALOG_H

# include <stdbool.h>
# include <stddef.h>

typedef struct s_flipnet_slice
{
	const char	*data;
	size_t		size;
} t_flipnet_slice;

bool	flipnet_slice_copy(char *output, size_t output_size,
			t_flipnet_slice input);

bool	flipnet_slice_copy_truncated(char *output, size_t output_size,
			t_flipnet_slice input);

bool	flipnet_slice_is_decimal(t_flipnet_slice value);

bool	flipnet_slice_is_upper_hex(t_flipnet_slice value);

bool	flipnet_slice_is_identifier(t_flipnet_slice value);

bool	flipnet_catalog_next_line(const char **cursor, t_flipnet_slice *line);

bool	flipnet_catalog_next_field(const char **cursor, const char *end,
			t_flipnet_slice *field);

bool	flipnet_catalog_split_pair(t_flipnet_slice line,
			t_flipnet_slice *left, t_flipnet_slice *right);

#endif
