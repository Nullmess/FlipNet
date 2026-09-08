#ifndef FLIPNET_BLE_H
# define FLIPNET_BLE_H

# include <stdbool.h>
# include <stddef.h>

typedef struct s_flipnet_ble	t_ble;

typedef struct s_flipnet_ble_callbacks
{
	void	(*line)(const char *line, void *context);
	void	(*status)(bool connected, bool advertising, void *context);
	void	*context;
}	t_ble_cbs;

typedef struct s_flipnet_payload
{
	const char	*data;
	size_t		length;
}	t_ble_payload;

t_ble			*flipnet_ble_alloc(t_ble_cbs callbacks);

void			flipnet_ble_free(t_ble *instance);

bool			flipnet_ble_send(t_ble *instance,
					t_ble_payload payload);

bool			flipnet_ble_is_connected(t_ble *instance);

#endif
