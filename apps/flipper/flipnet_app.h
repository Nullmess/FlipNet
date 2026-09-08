#ifndef FLIPNET_APP_H
# define FLIPNET_APP_H

# include "flipnet_ble.h"
# include "flipnet_capability_catalog.h"
# include "flipnet_protocol.h"
# include "flipnet_usb.h"

# include <furi.h>
# include <gui/gui.h>
# include <gui/modules/submenu.h>
# include <gui/modules/text_input.h>
# include <gui/view.h>
# include <gui/view_dispatcher.h>
# include <notification/notification_messages.h>

# define FLIPNET_VERSION "1.0.1"
# define FLIPNET_INPUT_PREFIX_SIZE 64U
# define FLIPNET_INPUT_SIZE 189U
# define FLIPNET_HISTORY_ITEMS 16U
# define FLIPNET_HISTORY_KEY 32U
# define FLIPNET_WIRE_ARGUMENT_SIZE 193U
# define FLIPNET_TX_SIZE 512U
# define FLIPNET_QUEUE_SIZE 8U
# define FLIPNET_ANIMATION_MS 180U
# define FLIPNET_TIMEOUT_FRAMES 55U
# define FLIPNET_CAPABILITIES_BLE_TIMEOUT_FRAMES 110U
# define FLIPNET_HOST_DISCOVERY_TIMEOUT_FRAMES 550U
# define FLIPNET_PORT_SCAN_TIMEOUT_FRAMES 1250U
# define FLIPNET_CONNECT_FRAMES 18U
# define FLIPNET_FEED_ITEMS 6U
# define FLIPNET_FEED_SOURCE 24U
# define FLIPNET_FEED_TITLE 151U
# define FLIPNET_WEATHER_ITEMS 8U
# define FLIPNET_WEATHER_ID 16U
# define FLIPNET_WEATHER_LABEL 64U
# define FLIPNET_WIKI_ITEMS 8U
# define FLIPNET_WIKI_ID 16U
# define FLIPNET_WIKI_LABEL 64U
# define FLIPNET_WEB_ITEMS 8U
# define FLIPNET_WEB_ID 17U
# define FLIPNET_WEB_LABEL 80U
# define FLIPNET_WEB_TIMEOUT_FRAMES 190U
# define FLIPNET_APP_ITEMS 16U
# define FLIPNET_APP_ID 25U
# define FLIPNET_APP_LABEL 49U
# define FLIPNET_APP_PROMPT 49U
# define FLIPNET_APP_INITIAL 81U
# define FLIPNET_APP_TIMEOUT_FRAMES 335U
# define FLIPNET_APP_CHOICE_ITEMS 20U
# define FLIPNET_APP_CHOICE_VALUE 49U
# define FLIPNET_APP_CHOICE_LABEL 73U
# define FLIPNET_EXTENSION_SETTING_ITEMS 8U
# define FLIPNET_PLUGIN_SETTING_ITEMS 10U
# define FLIPNET_SETTING_ID 25U
# define FLIPNET_SETTING_LABEL 65U
# define FLIPNET_RESULT_LINES 5U
# define FLIPNET_RESULT_PAGE_LINES 12U
# define FLIPNET_RESULT_TEXT_WIDTH 110U
# define FLIPNET_TEXT_LINE_SIZE 128U
# define FLIPNET_RESULT_MAX_PAGES \
	((FLIPNET_PROTOCOL_MAX_BODY + FLIPNET_RESULT_PAGE_LINES - 1U) \
		/ FLIPNET_RESULT_PAGE_LINES)

typedef enum e_flipnet_view
{
	VIEW_LANGUAGE,
	VIEW_HOME,
	VIEW_INTERNET,
	VIEW_OVERVIEW,
	VIEW_NETWORK,
	VIEW_DISCOVERY,
	VIEW_PORTS,
	VIEW_SECURITY,
	VIEW_SYSTEMS,
	VIEW_OPERATIONS,
	VIEW_ACTIONS,
	VIEW_MONITORS,
	VIEW_FEEDS,
	VIEW_FEEDS_FR,
	VIEW_FEEDS_EN,
	VIEW_FEED_CARDS,
	VIEW_WEB,
	VIEW_WEB_RESULTS,
	VIEW_APPS,
	VIEW_APP_CHOICES,
	VIEW_WORKSPACE,
	VIEW_TARGETS,
	VIEW_UTILITIES,
	VIEW_WEATHER_RESULTS,
	VIEW_WIKI_RESULTS,
	VIEW_SETTINGS,
	VIEW_EXTENSION_SETTINGS,
	VIEW_PLUGIN_SETTINGS,
	VIEW_PLUGIN_ACTIONS,
	VIEW_PLUGIN_CONFIRM,
	VIEW_LOADING,
	VIEW_INPUT,
	VIEW_RESULT
}	t_flipnet_view;

typedef enum e_flipnet_language
{
	LANGUAGE_UNSET,
	LANGUAGE_EN,
	LANGUAGE_FR
}	t_flipnet_language;

typedef enum e_flipnet_transport
{
	TRANSPORT_NONE,
	TRANSPORT_BLE,
	TRANSPORT_USB
}	t_flipnet_transport;

typedef enum e_flipnet_mode
{
	MODE_AUTO,
	MODE_USB,
	MODE_BLE
}	t_flipnet_mode;

typedef enum e_flipnet_route
{
	ROUTE_LOCAL,
	ROUTE_WAN,
	ROUTE_EVENT
}	t_flipnet_route;

typedef enum e_flipnet_event
{
	EVENT_TRANSPORT = 1,
	EVENT_LINE,
	EVENT_ANIMATION
}	t_flipnet_event;

typedef struct s_overview_model
{
	uint8_t		page;
	bool		loading;
	bool		failed;
	bool		valid;
	uint8_t		transport;
	uint8_t		language;
	uint16_t	monitors;
	uint16_t	monitor_ok;
	uint16_t	monitor_error;
	uint16_t	alerts;
	uint16_t	targets;
	uint16_t	favorites;
	uint16_t	history;
	uint16_t	devices;
	uint16_t	new_devices;
	uint16_t	missing_devices;
	uint16_t	changed_devices;
}	t_overview_model;

typedef struct s_loading_model
{
	uint8_t	route;
	uint16_t	frame;
	uint8_t	transport;
	uint8_t	progress;
	bool		determinate;
}	t_loading_model;

typedef enum e_flipnet_card
{
	CARD_SUCCESS,
	CARD_WARNING,
	CARD_FAILURE
}	t_flipnet_card;

typedef struct s_result_model
{
	uint16_t	page;
	uint16_t	pages;
	uint16_t	scroll;
	uint16_t	scroll_max;
	uint8_t		page_lines[FLIPNET_RESULT_MAX_PAGES];
	const char	*title;
	const char	*body;
	uint8_t		kind;
}	t_result_model;

typedef struct s_feed_model
{
	uint8_t	index;
	uint8_t	count;
	char	heading[48];
	char	sources[FLIPNET_FEED_ITEMS][FLIPNET_FEED_SOURCE];
	uint8_t	source_indices[FLIPNET_FEED_ITEMS];
	char	titles[FLIPNET_FEED_ITEMS][FLIPNET_FEED_TITLE];
}	t_feed_model;

typedef struct s_input_spec
{
	t_flipnet_view	parent;
	const char		*service;
	const char		*prompt;
	const char		*initial;
}	t_input_spec;

typedef struct s_flipnet_app
{
	Gui					*gui;
	NotificationApp		*notification;
	ViewDispatcher		*dispatcher;
	Submenu				*language_menu;
	Submenu				*home;
	Submenu				*internet;
	View				*overview;
	Submenu				*network;
	Submenu				*discovery;
	Submenu				*ports;
	Submenu				*security;
	Submenu				*systems;
	Submenu				*operations;
	Submenu				*actions;
	Submenu				*monitors;
	Submenu				*feeds;
	Submenu				*feeds_fr;
	Submenu				*feeds_en;
	View				*feed_cards;
	Submenu				*web;
	Submenu				*web_results;
	Submenu				*apps;
	Submenu				*app_choices;
	Submenu				*workspace;
	Submenu				*targets;
	Submenu				*utilities;
	Submenu				*weather_results;
	Submenu				*wiki_results;
	Submenu				*settings;
	Submenu				*extension_settings;
	Submenu				*plugin_settings;
	Submenu				*plugin_actions;
	Submenu				*plugin_confirm;
	View				*loading;
	TextInput			*input;
	View				*result;
	t_ble				*ble;
	t_usb				*usb;
	FuriMessageQueue	*lines;
	FuriTimer			*timer;
	bool				views_added;
	bool				restart_requested;
	bool				catalog_refresh_pending;
	bool				animation;
	bool				waiting_transport;
	bool				request_silent;
	bool				capabilities_attempted;
	bool				ble_connected;
	bool				usb_connected;
	uint16_t			animation_frame;
	t_flipnet_language	language;
	t_flipnet_mode		mode;
	t_flipnet_transport	active_transport;
	t_flipnet_transport	pending_transport;
	t_flipnet_transport	catalog_transport;
	t_flipnet_capabilities	*capabilities;
	t_flipnet_view		current_view;
	t_flipnet_view		return_view;
	t_flipnet_view		feed_parent;
	uint32_t			session_id;
	uint32_t			next_request;
	uint32_t			pending_request;
	char				pending_service[FLIPNET_PROTOCOL_MAX_SERVICE + 1U];
	char				pending_argument[FLIPNET_INPUT_SIZE];
	char				wire_argument[FLIPNET_WIRE_ARGUMENT_SIZE];
	char				input_buffer[FLIPNET_INPUT_SIZE];
	char				input_prefix[FLIPNET_INPUT_PREFIX_SIZE];
	char				input_history_key[FLIPNET_HISTORY_KEY];
	uint8_t			history_count;
	char				history_keys[FLIPNET_HISTORY_ITEMS]
					[FLIPNET_HISTORY_KEY];
	char				history_values[FLIPNET_HISTORY_ITEMS]
					[FLIPNET_INPUT_SIZE];
	char				tx_buffer[FLIPNET_TX_SIZE];
	char				result_title[FLIPNET_PROTOCOL_MAX_TITLE + 1U];
	char				result_body[FLIPNET_PROTOCOL_MAX_BODY + 1U];
	char				last_error[96];
	uint8_t			weather_count;
	char				weather_ids[FLIPNET_WEATHER_ITEMS][FLIPNET_WEATHER_ID];
	char				weather_labels[FLIPNET_WEATHER_ITEMS]
					[FLIPNET_WEATHER_LABEL];
	uint8_t			wiki_count;
	char				wiki_ids[FLIPNET_WIKI_ITEMS][FLIPNET_WIKI_ID];
	char				wiki_labels[FLIPNET_WIKI_ITEMS][FLIPNET_WIKI_LABEL];
	uint8_t			web_count;
	char				web_ids[FLIPNET_WEB_ITEMS][FLIPNET_WEB_ID];
	char				web_labels[FLIPNET_WEB_ITEMS][FLIPNET_WEB_LABEL];
	uint8_t			app_count;
	char				app_ids[FLIPNET_APP_ITEMS][FLIPNET_APP_ID];
	char				app_modes[FLIPNET_APP_ITEMS];
	char				app_labels[FLIPNET_APP_ITEMS][FLIPNET_APP_LABEL];
	char				app_prompts[FLIPNET_APP_ITEMS][FLIPNET_APP_PROMPT];
	char				app_initials[FLIPNET_APP_ITEMS][FLIPNET_APP_INITIAL];
	uint8_t			app_choice_count;
	char				app_choice_app_id[FLIPNET_APP_ID];
	char				app_choice_mode;
	char				app_translation_source[16];
	char				app_choice_values[FLIPNET_APP_CHOICE_ITEMS]
					[FLIPNET_APP_CHOICE_VALUE];
	char				app_choice_labels[FLIPNET_APP_CHOICE_ITEMS]
					[FLIPNET_APP_CHOICE_LABEL];
	uint8_t			extension_setting_count;
	char				extension_setting_ids[FLIPNET_EXTENSION_SETTING_ITEMS]
					[FLIPNET_SETTING_ID];
	char				extension_setting_states
					[FLIPNET_EXTENSION_SETTING_ITEMS];
	char				extension_setting_labels
					[FLIPNET_EXTENSION_SETTING_ITEMS]
					[FLIPNET_SETTING_LABEL];
	uint8_t			plugin_setting_count;
	char				plugin_setting_ids[FLIPNET_PLUGIN_SETTING_ITEMS]
					[FLIPNET_SETTING_ID];
	char				plugin_setting_states[FLIPNET_PLUGIN_SETTING_ITEMS];
	char				plugin_setting_labels[FLIPNET_PLUGIN_SETTING_ITEMS]
					[FLIPNET_SETTING_LABEL];
	char				selected_plugin_id[FLIPNET_SETTING_ID];
	char				selected_plugin_label[FLIPNET_SETTING_LABEL];
	char				selected_plugin_state;
}	t_flipnet_app;

t_flipnet_app	*flipnet_app_alloc(void);

void			flipnet_app_free(t_flipnet_app *app);

int32_t			flipnet_app(void *context);

#endif
