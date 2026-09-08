#ifndef FLIPNET_HISTORY_I_H
# define FLIPNET_HISTORY_I_H

# include "flipnet_app.h"

# include <storage/storage.h>

# define FLIPNET_HISTORY_PATH APP_DATA_PATH("input_history.tsv")
# define FLIPNET_HISTORY_FILE_SIZE 4096U

typedef struct s_flipnet_history_io
{
	Storage	*storage;
	File	*file;
	char	*buffer;
	size_t	size;
} t_flipnet_history_io;

#endif
