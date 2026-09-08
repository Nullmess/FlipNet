#include "flipnet_catalog.h"

#include <string.h>

bool	flipnet_catalog_next_line(const char **cursor, t_flipnet_slice *line)
{
	const char	*end;

	if (!cursor || !*cursor || !line || !**cursor)
		return (false);
	end = strchr(*cursor, '\n');
	line->data = *cursor;
	if (end)
		line->size = (size_t)(end - *cursor);
	else
		line->size = strlen(*cursor);
	if (line->size && line->data[line->size - 1U] == '\r')
		line->size--;
	if (end)
		*cursor = end + 1;
	else
		*cursor += strlen(*cursor);
	return (true);
}

bool	flipnet_catalog_next_field(const char **cursor, const char *end,
			t_flipnet_slice *field)
{
	const char	*separator;

	if (!cursor || !*cursor || !end || !field || *cursor > end)
		return (false);
	separator = memchr(*cursor, '|', (size_t)(end - *cursor));
	field->data = *cursor;
	if (!separator)
	{
		field->size = (size_t)(end - *cursor);
		*cursor = end;
		return (true);
	}
	field->size = (size_t)(separator - *cursor);
	*cursor = separator + 1;
	return (true);
}

bool	flipnet_catalog_split_pair(t_flipnet_slice line,
			t_flipnet_slice *left, t_flipnet_slice *right)
{
	const char	*separator;

	if (!line.data || !left || !right)
		return (false);
	separator = memchr(line.data, '|', line.size);
	if (!separator)
		return (false);
	left->data = line.data;
	left->size = (size_t)(separator - line.data);
	right->data = separator + 1;
	right->size = line.size - left->size - 1U;
	return (left->size > 0U && right->size > 0U);
}
