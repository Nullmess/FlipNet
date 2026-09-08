#include "flipnet_features_generated.h"
#include "flipnet_menu.h"

static void	flipnet_menu_feed_items(Submenu *menu, t_flipnet_app *app,
			const t_flipnet_menu_service *items)
{
	uint8_t	index;

	index = 0U;
	while (index < 11U)
	{
		flipnet_menu_service(menu, app, items[index]);
		index++;
	}
}

void	flipnet_menu_feeds_french(Submenu *menu, t_flipnet_app *app)
{
	static const t_flipnet_menu_service	items[] = {
		{"[N] Actualites", 0U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[T] Numerique", 1U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[C] Cyber", 2U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[S] Systemes", 3U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[A] Mix francais", 4U, FLIPNET_FEATURE_FEED_DIGEST},
		{"Le Monde", 5U, FLIPNET_FEATURE_HEADLINES},
		{"franceinfo", 6U, FLIPNET_FEATURE_HEADLINES},
		{"Numerama", 7U, FLIPNET_FEATURE_HEADLINES},
		{"01net", 8U, FLIPNET_FEATURE_HEADLINES},
		{"LinuxFr.org", 9U, FLIPNET_FEATURE_HEADLINES},
		{"CERT-FR", 10U, FLIPNET_FEATURE_HEADLINES},
	};

	flipnet_menu_feed_items(menu, app, items);
}

void	flipnet_menu_feeds_english(Submenu *menu, t_flipnet_app *app)
{
	static const t_flipnet_menu_service	items[] = {
		{"[N] World news", 0U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[T] Technology", 1U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[C] Cyber", 2U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[S] Systems", 3U, FLIPNET_FEATURE_FEED_DIGEST},
		{"[A] Mixed English", 4U, FLIPNET_FEATURE_FEED_DIGEST},
		{"BBC News", 5U, FLIPNET_FEATURE_HEADLINES},
		{"The Guardian", 6U, FLIPNET_FEATURE_HEADLINES},
		{"Ars Technica", 7U, FLIPNET_FEATURE_HEADLINES},
		{"The Verge", 8U, FLIPNET_FEATURE_HEADLINES},
		{"BleepingComputer", 9U, FLIPNET_FEATURE_HEADLINES},
		{"LWN.net", 10U, FLIPNET_FEATURE_HEADLINES},
	};

	flipnet_menu_feed_items(menu, app, items);
}
