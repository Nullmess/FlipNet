#include "flipnet_app.h"
#include "flipnet_ui.h"

#include <stdio.h>

bool	flipnet_result_line(Canvas *canvas, const char **cursor, char *output,
			size_t size);
void	flipnet_result_layout(Canvas *canvas, t_result_model *model);

static void	flipnet_result_skip(Canvas *canvas, const char **cursor,
			uint16_t count)
{
	char	line[FLIPNET_TEXT_LINE_SIZE];

	while (count > 0U
		&& flipnet_result_line(canvas, cursor, line, sizeof(line)))
		count--;
}

static void	flipnet_result_lines(Canvas *canvas, t_result_model *model)
{
	char		line[FLIPNET_TEXT_LINE_SIZE];
	const char	*cursor;
	uint16_t	skip;
	uint8_t		row;

	cursor = model->body;
	skip = model->page * FLIPNET_RESULT_PAGE_LINES + model->scroll;
	flipnet_result_skip(canvas, &cursor, skip);
	row = 0U;
	while (row < FLIPNET_RESULT_LINES
		&& flipnet_result_line(canvas, &cursor, line, sizeof(line)))
	{
		canvas_draw_str(canvas, 7, 28 + row * 8, line);
		row++;
	}
}

static void	flipnet_result_scrollbar(Canvas *canvas, t_result_model *model)
{
	uint8_t	height;
	uint8_t	y;

	if (model->scroll_max == 0U)
		return ;
	canvas_draw_line(canvas, 122, 18, 122, 60);
	height = 8U;
	y = 18U + (uint8_t)((34U * model->scroll) / model->scroll_max);
	canvas_draw_line(canvas, 120, y, 120, y + height);
	canvas_draw_line(canvas, 121, y, 121, y + height);
	canvas_draw_line(canvas, 122, y, 122, y + height);
}

void	flipnet_result_draw(Canvas *canvas, void *model_pointer)
{
	t_result_model	*model;
	char			page[12];

	model = model_pointer;
	canvas_clear(canvas);
	flipnet_status_bar(canvas, model->title, TRANSPORT_NONE);
	canvas_set_font(canvas, FontSecondary);
	flipnet_result_layout(canvas, model);
	snprintf(page, sizeof(page), "%u/%u", model->page + 1U, model->pages);
	canvas_set_font(canvas, FontSecondary);
	canvas_draw_str_aligned(canvas, 126, 8, AlignRight, AlignCenter, page);
	canvas_draw_rframe(canvas, 2, 16, 124, 47, 4U);
	flipnet_result_lines(canvas, model);
	flipnet_result_scrollbar(canvas, model);
}

bool	flipnet_result_input(InputEvent *event, void *context)
{
	t_flipnet_app	*app;
	t_result_model	*model;
	bool			changed;

	app = context;
	if (event->type != InputTypeShort && event->type != InputTypeRepeat)
		return (false);
	model = view_get_model(app->result);
	changed = false;
	if (event->key == InputKeyLeft && model->page > 0U)
		changed = (--model->page, model->scroll = 0U, true);
	if (event->key == InputKeyRight && model->page + 1U < model->pages)
		changed = (++model->page, model->scroll = 0U, true);
	model->scroll_max = 0U;
	if (model->page_lines[model->page] > FLIPNET_RESULT_LINES)
		model->scroll_max = model->page_lines[model->page]
			- FLIPNET_RESULT_LINES;
	if (event->key == InputKeyUp && model->scroll > 0U)
		changed = (--model->scroll, true);
	if (event->key == InputKeyDown && model->scroll < model->scroll_max)
		changed = (++model->scroll, true);
	view_commit_model(app->result, changed);
	return (changed);
}
