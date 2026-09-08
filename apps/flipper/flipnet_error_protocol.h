#ifndef FLIPNET_ERROR_PROTOCOL_H
# define FLIPNET_ERROR_PROTOCOL_H

# include <stdbool.h>
# include <stddef.h>

# define FLIPNET_ERROR_CODE_SIZE 41U

typedef struct s_flipnet_error_result
{
	char	code[FLIPNET_ERROR_CODE_SIZE];
	bool	recoverable;
} t_flipnet_error_result;

bool	flipnet_error_protocol_apply(char *payload, size_t payload_size,
			bool french, t_flipnet_error_result *result);

#endif
