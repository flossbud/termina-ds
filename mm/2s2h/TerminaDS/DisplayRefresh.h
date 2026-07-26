#ifndef TERMINADS_DISPLAY_REFRESH_H
#define TERMINADS_DISPLAY_REFRESH_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void TerminaDS_SetDisplayRefreshHz(int32_t hz);

/* Returns 0 until Android has supplied the main display's active rate. */
int32_t TerminaDS_GetDisplayRefreshHz(void);

#ifdef __cplusplus
}
#endif

#endif /* TERMINADS_DISPLAY_REFRESH_H */
