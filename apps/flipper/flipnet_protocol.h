#ifndef FLIPNET_PROTOCOL_H
# define FLIPNET_PROTOCOL_H

# include <stdbool.h>
# include <stddef.h>
# include <stdint.h>

# define FLIPNET_PROTOCOL_VERSION "FN2"
# define FLIPNET_PROTOCOL_CAPS "hello,progress,event,cancel,priority,crc32"
# define FLIPNET_PROTOCOL_MAX_SERVICE 32U
# define FLIPNET_PROTOCOL_MAX_TITLE 64U
# define FLIPNET_PROTOCOL_MAX_BODY 1024U
# define FLIPNET_PROTOCOL_MAX_ARGUMENT 192U
# define FLIPNET_PROTOCOL_MAX_LINE 2304U

typedef enum e_flipnet_response_status
{
	FLIPNET_RESPONSE_OK,
	FLIPNET_RESPONSE_ERROR
}	t_flipnet_response_status;

typedef struct s_flipnet_response
{
	uint32_t	session_id;
	uint32_t	request_id;
	uint8_t		status;
	char		title[FLIPNET_PROTOCOL_MAX_TITLE + 1U];
	char		body[FLIPNET_PROTOCOL_MAX_BODY + 1U];
}	t_flipnet_response;

typedef struct s_flipnet_request
{
	char			*output;
	size_t			output_size;
	uint32_t		session_id;
	uint32_t		request_id;
	uint8_t			priority;
	const char		*service;
	const char		*argument;
}	t_flipnet_request;

bool		flipnet_protocol_build_request(t_flipnet_request *request);

bool		flipnet_protocol_parse_response(const char *line,
				t_flipnet_response *response);

bool		flipnet_protocol_hex_encode(const uint8_t *input, size_t input_size,
				char *output, size_t output_size);

bool		flipnet_protocol_hex_decode(const char *input, uint8_t *output,
				size_t output_size, size_t *decoded_size);

uint32_t	flipnet_protocol_crc32(const uint8_t *data, size_t size);

#endif
