#include "flipnet_app.h"
#include "flipnet_ui.h"

#include <string.h>

bool	flipnet_result_line(Canvas *canvas, const char **cursor, char *output,
			size_t size)
{
	return (flipnet_text_line(canvas, (t_flipnet_text_request){
			cursor, output, size, FLIPNET_RESULT_TEXT_WIDTH}));
}

static uint16_t	flipnet_result_count_lines(Canvas *canvas,
			t_result_model *model)
{
	char		line[FLIPNET_TEXT_LINE_SIZE];
	const char	*cursor;
	uint16_t	lines;
	uint16_t	page;

	cursor = model->body;
	lines = 0U;
	while (flipnet_result_line(canvas, &cursor, line, sizeof(line)))
	{
		page = lines / FLIPNET_RESULT_PAGE_LINES;
		if (page >= FLIPNET_RESULT_MAX_PAGES)
			break ;
		model->page_lines[page]++;
		lines++;
	}
	return (lines);
}

static void	flipnet_result_set_pages(t_result_model *model, uint16_t lines)
{
	model->pages = 1U;
	if (lines > 0U)
		model->pages = (lines + FLIPNET_RESULT_PAGE_LINES - 1U)
			/ FLIPNET_RESULT_PAGE_LINES;
	if (model->page >= model->pages)
		model->page = model->pages - 1U;
}

void	flipnet_result_layout(Canvas *canvas, t_result_model *model)
{
	uint16_t	lines;

	memset(model->page_lines, 0, sizeof(model->page_lines));
	lines = flipnet_result_count_lines(canvas, model);
	flipnet_result_set_pages(model, lines);
	model->scroll_max = 0U;
	if (model->page_lines[model->page] > FLIPNET_RESULT_LINES)
		model->scroll_max = model->page_lines[model->page]
			- FLIPNET_RESULT_LINES;
	if (model->scroll > model->scroll_max)
		model->scroll = model->scroll_max;
}
