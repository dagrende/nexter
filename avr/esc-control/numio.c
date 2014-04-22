// numeric io library
// uses uartio

#include <avr/io.h>
#include <avr/interrupt.h>
#include <avr/pgmspace.h>
#include "numio.h"
#include "uartio.h"

u08 isNumberFormatErr;

u08 isNumErr(void) {
    return isNumberFormatErr;
}

void clearNumErr(void) {
    isNumberFormatErr = 0;
}

void putds16(s16 v) {
	if (v < 0) {
		putch('-');
		putdu16(-v);
	} else {
		putdu16(v);
	}
}

void putdu16_2(u16 v) {
    if (v > 0) {
    	putdu16_2(v / 10);
    	putch('0' + v % 10);
    }
}

void putdu16(u16 v) {
    if (v == 0) {
    	putch('0');
    } else {
    	putdu16_2(v);
    }
}

void puthu04(u08 data) {
    // Send 4-bit hex value
    u08 ch = data & 0x0f;
    if (ch > 9) {
        ch += 'A' - 10;
    } else {
        ch += '0';
    }
    putch(ch);
}

void puthu08(u08 data) {
    /* Send 8-bit hex value */
    puthu04(data >> 4);
    puthu04(data);
}

void puthu16(u16 data) {
    /* Send 16-bit hex value */
    puthu08(data >> 8);
    puthu08(data);
}

// print 32 bit hex word to serial port
void puthu32(int32_t i) {
    /* Send 32-bit hex value */
    puthu16(i >> 16);
    puthu16(i);
}

u08 isDigit(u08 d) {
    return ('0' <= d && d <= '9');
}

s16 getds16() {
	s16 i = 0;
	boolean neg = false;
	u08 ch = getch();
	if (ch == '-') {
		neg = true;
		ch = getch();
	}
	while (isDigit(ch)) {
		ch -= '0';
		i = i * 10 + ch;
		ch = getch();
	}
	ungetch(ch);
	if (neg) {
		i = -i;
	}
	return i;
}


