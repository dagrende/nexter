#ifndef __UARTIO_H__
#define __UARTIO_H__

#include <avr/io.h>
#include <avr/interrupt.h>
#include <avr/pgmspace.h>
#include "types.h"


#define RATE_FACTOR(clockFreq, baudRate) (clockFreq / (baudRate * 16l) - 1)

void uartInit(u16 rateFactor);

u08 getch(void);
void ungetch(u08 ch);
u08 charAvail(void);
u08 lineAvail(void);
void clearNumErr(void);
u08 isNumErr(void);

void putch(u08 ch);
void putstr(char *s);
void putps(u08 *ps);
void eol(void);

#define PRINT(str) (putps(PSTR(str)))

#endif

