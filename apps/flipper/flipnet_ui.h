#ifndef FLIPNET_UI_H
# define FLIPNET_UI_H

# include "flipnet_app.h"

typedef struct s_flipnet_text_request
{
	const char	**cursor;
	char		*output;
	size_t		output_size;
	uint8_t		width;
} t_flipnet_text_request;

void	flipnet_result_set(t_flipnet_app *app, const char *title,
			const char *body);

void	flipnet_flow_begin(t_flipnet_app *app);

void	flipnet_flow_stop(t_flipnet_app *app);

void	flipnet_flow_result(t_flipnet_app *app, const char *title,
			const char *body, t_flipnet_card kind);

void	flipnet_flow_overview(t_flipnet_app *app, const char *body);

bool	flipnet_flow_feed(t_flipnet_app *app, const char *body);

void	flipnet_flow_timeout(t_flipnet_app *app);

void	flipnet_result_set_kind(t_flipnet_app *app, const char *title,
			const char *body, t_flipnet_card kind);

void	flipnet_status_bar(Canvas *canvas, const char *title,
			uint8_t transport);

bool	flipnet_text_line(Canvas *canvas, t_flipnet_text_request request);

void	flipnet_result_render(t_flipnet_app *app);

void	flipnet_loading_set(t_flipnet_app *app, const char *service);

void	flipnet_loading_tick(t_flipnet_app *app);

void	flipnet_loading_progress(t_flipnet_app *app, uint8_t progress);

void	flipnet_overview_loading(t_flipnet_app *app);

void	flipnet_overview_failed(t_flipnet_app *app);

void	flipnet_overview_transport(t_flipnet_app *app);

bool	flipnet_overview_update(t_flipnet_app *app, const char *body);

bool	flipnet_feed_update(t_flipnet_app *app, const char *body);

bool	flipnet_weather_show(t_flipnet_app *app, const char *body);

bool	flipnet_wikipedia_show(t_flipnet_app *app, const char *body);

bool	flipnet_web_show(t_flipnet_app *app, const char *body);

bool	flipnet_apps_open_cached(t_flipnet_app *app);

bool	flipnet_apps_choices_show(t_flipnet_app *app, const char *body);

void	flipnet_error_show(t_flipnet_app *app, const char *title,
			const char *message);

void	flipnet_diagnostics_show(t_flipnet_app *app);

View	*flipnet_result_alloc(t_flipnet_app *app);

View	*flipnet_loading_alloc(t_flipnet_app *app);

View	*flipnet_overview_alloc(t_flipnet_app *app);

View	*flipnet_feed_alloc(t_flipnet_app *app);

#endif
