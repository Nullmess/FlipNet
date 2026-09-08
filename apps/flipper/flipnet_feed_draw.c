#include "flipnet_features_generated.h"
#include "flipnet_feed_i.h"
#include "flipnet_app.h"
#include "flipnet_core.h"
#include "flipnet_ui.h"

#include <stdio.h>
#include <string.h>

static const t_feed_source_label	g_feed_sources[] = {
	{"fr_certfr", "CERT-FR"},
	{"fr_lemonde", "Le Monde"},
	{"fr_lemonde_pixels", "Le Monde Pixels"},
	{"fr_franceinfo", "franceinfo"},
	{"fr_france24", "France 24"},
	{"fr_rfi", "RFI"},
	{"fr_lefigaro", "Le Figaro"},
	{"fr_liberation", "Liberation"},
	{"fr_20minutes", "20 Minutes"},
	{"fr_bfmtv", "BFMTV"},
	{"fr_numerama", "Numerama"},
	{"fr_01net", "01net"},
	{"fr_clubic", "Clubic"},
	{"fr_nextink", "Next.ink"},
	{"fr_jdg", "JDG"},
	{"fr_frandroid", "Frandroid"},
	{"fr_lesnumeriques", "Les Numeriques"},
	{"fr_korben", "Korben"},
	{"fr_linuxfr", "LinuxFr"},
	{"en_bbc", "BBC News"},
	{"en_guardian", "The Guardian"},
	{"en_nyt", "NY Times"},
	{"en_aljazeera", "Al Jazeera"},
	{"en_npr", "NPR"},
	{"en_ars", "Ars Technica"},
	{"en_verge", "The Verge"},
	{"en_techcrunch", "TechCrunch"},
	{"en_wired", "Wired"},
	{"en_engadget", "Engadget"},
	{"en_hn", "Hacker News"},
	{"en_redhat", "Red Hat"},
	{"en_lwn", "LWN"},
	{"en_ubuntu", "Ubuntu"},
	{"en_docker", "Docker"},
	{"en_kubernetes", "Kubernetes"},
	{"en_cloudflare", "Cloudflare"},
	{"en_cisa", "CISA"},
	{"en_bleeping", "BleepingComputer"},
	{"en_krebs", "Krebs"},
	{"en_thn", "Hacker News Sec"},
	{"en_google_sec", "Google Security"},
	{"en_ms_sec", "Microsoft Sec"},
	{"en_schneier", "Schneier"},
};

static const char	*flipnet_feed_source(const char *source)
{
	size_t	index;

	index = 0U;
	while (index < sizeof(g_feed_sources) / sizeof(g_feed_sources[0]))
	{
		if (!strcmp(source, g_feed_sources[index].id))
			return (g_feed_sources[index].label);
		index++;
	}
	return (source);
}

static void	flipnet_feed_title(Canvas *canvas, const char *title)
{
	char		line[FLIPNET_FEED_TITLE];
	const char	*cursor;
	uint8_t		row;

	cursor = title;
	row = 0U;
	while (row < 4U && flipnet_text_line(canvas, (t_flipnet_text_request){
			&cursor, line, sizeof(line), 114U}))
	{
		canvas_draw_str(canvas, 7, 29 + row * 9, line);
		row++;
	}
}

void	flipnet_feed_draw(Canvas *canvas, void *model_pointer)
{
	t_feed_model	*model;
	char			page[12];
	const char		*source;

	model = model_pointer;
	canvas_clear(canvas);
	canvas_set_font(canvas, FontPrimary);
	canvas_draw_str(canvas, 2, 9, model->heading);
	snprintf(page, sizeof(page), "%u/%u", model->index + 1U, model->count);
	canvas_set_font(canvas, FontSecondary);
	canvas_draw_str_aligned(canvas, 126, 8, AlignRight, AlignCenter, page);
	canvas_draw_rframe(canvas, 2, 13, 124, 50, 4U);
	source = flipnet_feed_source(model->sources[model->index]);
	canvas_draw_str(canvas, 7, 21, source);
	flipnet_feed_title(canvas, model->titles[model->index]);
}
