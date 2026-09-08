#ifndef FLIPNET_CAPABILITY_CATALOG_H
# define FLIPNET_CAPABILITY_CATALOG_H

# include <stdbool.h>
# include <stddef.h>
# include <stdint.h>

# define FLIPNET_CAPABILITY_PLATFORM_SIZE 16U
# define FLIPNET_CAPABILITY_REVISION_SIZE 13U
# define FLIPNET_CAPABILITY_SERVICES_SIZE 1027U
# define FLIPNET_CAPABILITY_FEATURE_ITEMS 64U
# define FLIPNET_CAPABILITY_APP_ITEMS 16U
# define FLIPNET_CAPABILITY_ID_SIZE 33U
# define FLIPNET_CAPABILITY_DOMAIN_SIZE 13U
# define FLIPNET_CAPABILITY_GROUP_SIZE 25U
# define FLIPNET_CAPABILITY_KIND_SIZE 21U
# define FLIPNET_CAPABILITY_CATEGORY_SIZE 13U
# define FLIPNET_CAPABILITY_MODE_SIZE 21U
# define FLIPNET_CAPABILITY_LABEL_SIZE 49U
# define FLIPNET_CAPABILITY_PROMPT_SIZE 49U
# define FLIPNET_CAPABILITY_INITIAL_SIZE 81U
# define FLIPNET_CAPABILITY_EXTENSION_SIZE 21U

typedef enum e_flipnet_catalog_result
{
	FLIPNET_CATALOG_INVALID,
	FLIPNET_CATALOG_MORE,
	FLIPNET_CATALOG_COMPLETE
} t_flipnet_catalog_result;

typedef struct s_flipnet_remote_app
{
	char	id[25];
	char	service[FLIPNET_CAPABILITY_ID_SIZE];
	char	mode;
	char	title_en[FLIPNET_CAPABILITY_LABEL_SIZE];
	char	title_fr[FLIPNET_CAPABILITY_LABEL_SIZE];
	char	prompt_en[FLIPNET_CAPABILITY_PROMPT_SIZE];
	char	prompt_fr[FLIPNET_CAPABILITY_PROMPT_SIZE];
	char	initial[FLIPNET_CAPABILITY_INITIAL_SIZE];
	char	origin[FLIPNET_CAPABILITY_KIND_SIZE];
	char	extension_group[FLIPNET_CAPABILITY_EXTENSION_SIZE];
} t_flipnet_remote_app;

typedef struct s_flipnet_capabilities
{
	bool	loaded;
	bool	loading;
	char	platform[FLIPNET_CAPABILITY_PLATFORM_SIZE];
	char	revision[FLIPNET_CAPABILITY_REVISION_SIZE];
	char	services[FLIPNET_CAPABILITY_SERVICES_SIZE];
	uint8_t	next_page;
	uint8_t	total_pages;
	uint8_t	feature_count;
	uint8_t	app_count;
	t_flipnet_remote_app		apps[FLIPNET_CAPABILITY_APP_ITEMS];
} t_flipnet_capabilities;

void	flipnet_capability_catalog_init(t_flipnet_capabilities *catalog);

t_flipnet_catalog_result	flipnet_capability_catalog_parse(
			t_flipnet_capabilities *catalog, const char *payload);

bool	flipnet_capability_catalog_has(const t_flipnet_capabilities *catalog,
			const char *service);

#endif
