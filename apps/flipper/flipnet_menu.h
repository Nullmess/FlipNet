#ifndef FLIPNET_MENU_H
# define FLIPNET_MENU_H

# include "flipnet_app.h"

typedef enum e_flipnet_menu_navigation
{
	FLIPNET_MENU_HOME_NETWORK = 1000U,
	FLIPNET_MENU_HOME_DEVICE,
	FLIPNET_MENU_HOME_INTERNET,
	FLIPNET_MENU_HOME_TOOLS,
	FLIPNET_MENU_HOME_AUTOMATION,
	FLIPNET_MENU_HOME_WORKSPACE,
	FLIPNET_MENU_HOME_PLUGINS,
	FLIPNET_MENU_HOME_SETTINGS,
	FLIPNET_MENU_NETWORK_INSPECTION = 1100U,
	FLIPNET_MENU_NETWORK_DISCOVERY,
	FLIPNET_MENU_NETWORK_INVENTORY,
	FLIPNET_MENU_AUTOMATION_MONITORING = 1200U,
	FLIPNET_MENU_AUTOMATION_ACTIONS
} t_flipnet_menu_navigation;

typedef struct s_flipnet_menu_service
{
	const char	*label;
	uint32_t	index;
	const char	*service;
} t_flipnet_menu_service;

typedef struct s_flipnet_menu_group
{
	const char		*label;
	uint32_t		index;
	const char *const	*services;
	size_t			count;
} t_flipnet_menu_group;


bool	flipnet_menus_alloc(t_flipnet_app *app);

void	flipnet_menus_free(t_flipnet_app *app);

void	flipnet_menus_refresh(t_flipnet_app *app);

void	flipnet_views_add(t_flipnet_app *app);

void	flipnet_views_remove(t_flipnet_app *app);

void	flipnet_menu_callback(void *context, uint32_t index);

void	flipnet_menu_feature_callback(void *context, uint32_t index);

bool	flipnet_menu_parent_has(t_flipnet_app *app, const char *parent);

void	flipnet_menu_build_parent(Submenu *menu, t_flipnet_app *app,
			const char *parent);

Submenu	*flipnet_submenu(const char *header);

void	flipnet_menu_service(Submenu *menu, t_flipnet_app *app,
			t_flipnet_menu_service item);

void	flipnet_menu_group(Submenu *menu, t_flipnet_app *app,
			t_flipnet_menu_group item);

#endif
