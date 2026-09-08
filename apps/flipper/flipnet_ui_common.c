#include "flipnet_ui.h"

#include <string.h>

static size_t	flipnet_text_fit(Canvas *canvas,
			t_flipnet_text_request *request, size_t *last_space)
{
	const char	*text;
	size_t		length;

	text = *request->cursor;
	length = 0U;
	*last_space = 0U;
	while (text[length] && text[length] != '\n'
		&& length + 1U < request->output_size)
	{
		request->output[length] = text[length];
		request->output[length + 1U] = '\0';
		if (canvas_string_width(canvas, request->output) > request->width)
			break ;
		if (text[length] == ' ')
			*last_space = length + 1U;
		length++;
	}
	return (length);
}

static void	flipnet_text_trim(t_flipnet_text_request *request,
			const char *text, size_t length, size_t consumed)
{
	while (length > 0U && text[length - 1U] == ' ')
		length--;
	memcpy(request->output, text, length);
	request->output[length] = '\0';
	*request->cursor = text + consumed;
	while (**request->cursor == ' ')
		(*request->cursor)++;
	if (**request->cursor == '\n')
		(*request->cursor)++;
}

static bool	flipnet_text_empty_line(t_flipnet_text_request *request)
{
	const char	*text;

	text = *request->cursor;
	if (*text != '\n')
		return (false);
	request->output[0] = '\0';
	*request->cursor = text + 1;
	return (true);
}

bool	flipnet_text_line(Canvas *canvas, t_flipnet_text_request request)
{
	const char	*text;
	size_t		length;
	size_t		last_space;
	size_t		consumed;

	if (!canvas || !request.cursor || !*request.cursor || !**request.cursor
		|| !request.output || request.output_size < 2U || request.width == 0U)
		return (false);
	if (flipnet_text_empty_line(&request))
		return (true);
	text = *request.cursor;
	length = flipnet_text_fit(canvas, &request, &last_space);
	if (length == 0U)
		length = 1U;
	consumed = length;
	if (text[length] && text[length] != '\n' && last_space > 0U)
	{
		length = last_space - 1U;
		consumed = last_space;
	}
	flipnet_text_trim(&request, text, length, consumed);
	return (true);
}

void	flipnet_status_bar(Canvas *canvas, const char *title, uint8_t transport)
{
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str(canvas, 2, 9, title);
	canvas_set_font(canvas, FontSecondary);
	if (transport == TRANSPORT_USB)
		canvas_draw_str_aligned(canvas, 126, 8, AlignRight, AlignCenter, "USB");
	else if (transport == TRANSPORT_BLE)
		canvas_draw_str_aligned(canvas, 126, 8, AlignRight, AlignCenter, "BLE");
	canvas_draw_line(canvas, 2, 12, 126, 12);
}
