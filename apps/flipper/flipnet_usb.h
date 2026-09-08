#ifndef FLIPNET_USB_H
# define FLIPNET_USB_H

# include <stdbool.h>
# include <stddef.h>

typedef struct s_flipnet_usb	t_usb;

typedef struct s_flipnet_usb_callbacks
{
	void	(*line)(const char *line, void *context);
	void	(*status)(bool connected, void *context);
	void	*context;
}	t_usb_cbs;

typedef struct s_flipnet_usb_payload
{
	const char	*data;
	size_t		length;
}	t_usb_payload;

t_usb			*flipnet_usb_alloc(t_usb_cbs callbacks);

void			flipnet_usb_free(t_usb *instance);

bool			flipnet_usb_send(t_usb *instance,
					t_usb_payload payload);

bool			flipnet_usb_is_connected(t_usb *instance);

#endif
