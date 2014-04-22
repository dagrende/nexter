#ifndef __NUMIO_H__
#define __NUMIO_H__

#include <avr/io.h>
#include "types.h"

void putds16(s16 v);
void putdu16(u16 v);
void puthu08(u08 i);
void puthu16(u16 i);
void puthu32(int32_t i);

u08 isNumErr(void);
s16 getds16(void);	// get signed 16 bit decimal number, ending in a non digit

#endif

