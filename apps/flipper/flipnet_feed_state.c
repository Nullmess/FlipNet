#include "flipnet_app.h"
#include "flipnet_ui.h"

void	flipnet_feed_draw(Canvas *canvas, void *model_pointer);
bool	flipnet_feed_input(InputEvent *event, void *context);

View	*flipnet_feed_alloc(t_flipnet_app *app)
{
	View	*view;

	view = view_alloc();
	view_allocate_model(view, ViewModelTypeLocking, sizeof(t_feed_model));
	view_set_context(view, app);
	view_set_draw_callback(view, flipnet_feed_draw);
	view_set_input_callback(view, flipnet_feed_input);
	return (view);
}
