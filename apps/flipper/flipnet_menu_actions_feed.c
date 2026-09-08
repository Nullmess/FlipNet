#include "flipnet_features_generated.h"
#include "flipnet_core.h"
#include "flipnet_i18n.h"
#include "flipnet_menu_actions_feed.h"

static const t_flipnet_feed_request	g_feed_fr[] = {
	{FLIPNET_FEATURE_FEED_DIGEST, "fr_news"},
	{FLIPNET_FEATURE_FEED_DIGEST, "fr_tech"},
	{FLIPNET_FEATURE_FEED_DIGEST, "fr_cyber"},
	{FLIPNET_FEATURE_FEED_DIGEST, "fr_systems"},
	{FLIPNET_FEATURE_FEED_DIGEST, "fr_all"},
	{FLIPNET_FEATURE_HEADLINES, "fr_lemonde"},
	{FLIPNET_FEATURE_HEADLINES, "fr_franceinfo"},
	{FLIPNET_FEATURE_HEADLINES, "fr_numerama"},
	{FLIPNET_FEATURE_HEADLINES, "fr_01net"},
	{FLIPNET_FEATURE_HEADLINES, "fr_linuxfr"},
	{FLIPNET_FEATURE_HEADLINES, "fr_certfr"},
};

static const t_flipnet_feed_request	g_feed_en[] = {
	{FLIPNET_FEATURE_FEED_DIGEST, "en_news"},
	{FLIPNET_FEATURE_FEED_DIGEST, "en_tech"},
	{FLIPNET_FEATURE_FEED_DIGEST, "en_cyber"},
	{FLIPNET_FEATURE_FEED_DIGEST, "en_systems"},
	{FLIPNET_FEATURE_FEED_DIGEST, "en_all"},
	{FLIPNET_FEATURE_HEADLINES, "en_bbc"},
	{FLIPNET_FEATURE_HEADLINES, "en_guardian"},
	{FLIPNET_FEATURE_HEADLINES, "en_ars"},
	{FLIPNET_FEATURE_HEADLINES, "en_verge"},
	{FLIPNET_FEATURE_HEADLINES, "en_bleeping"},
	{FLIPNET_FEATURE_HEADLINES, "en_lwn"},
};

static void	flipnet_feed_send(t_flipnet_app *app, uint32_t index,
			t_flipnet_view parent, const t_flipnet_feed_request *items)
{
	if (index >= 11U)
		return ;
	flipnet_request_direct(app, parent, items[index].service,
		items[index].argument);
}

void	flipnet_menu_feeds_action(t_flipnet_app *app, uint32_t index)
{
	if (app->language == LANGUAGE_FR)
		flipnet_feed_send(app, index, VIEW_FEEDS, g_feed_fr);
	else
		flipnet_feed_send(app, index, VIEW_FEEDS, g_feed_en);
}

void	flipnet_menu_feeds_fr_action(t_flipnet_app *app, uint32_t index)
{
	flipnet_feed_send(app, index, VIEW_FEEDS_FR, g_feed_fr);
}

void	flipnet_menu_feeds_en_action(t_flipnet_app *app, uint32_t index)
{
	flipnet_feed_send(app, index, VIEW_FEEDS_EN, g_feed_en);
}
