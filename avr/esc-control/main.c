/*
 * main.c
 * Control power on 4 escs.
 * - generate RC receiver compatible pulses 1-2ms every 20ms on PB0-3
 * - pulse length controlled with commands on bluetooth module serial 9600bps:
 *    pp0 p1 p2 p3\r - sets power of of motor 0-3, where pi is 00-99
 * - avr ATmega88 with 20MHz external crystal
 * - bluetooth module RF-BT0417C (http://www.mdfly.com/index.php?main_page=product_info&products_id=63)
 *
 *
 *  Created on: Feb 23, 2011
 *      Author: dag
 */

#include <avr/interrupt.h>
#include <avr/io.h>
#include "uartio.h"
#include "numio.h"

#define MOTOR_COUNT 4
// turn off motors if no command in 2s
#define MAX_TICKS_BETWEEN_COMMANDS 100
// speed limit as max motor power is not needed
#define POWER_LIMIT 200
// max speed value
#define MAX_SPEED 255
// esc pulse timer value for 1ms
#define TIMER_1MS 2500
// speed multiplier to get esc pulse timer value (rounded)
#define SPEED_TIMER_MULT ((TIMER_1MS + MAX_SPEED / 2) / MAX_SPEED)

// 0-19 wait, 20 start generating esc pulses for each motor
volatile u08 escSignalPhase = 0;
volatile u08 speed[MOTOR_COUNT] = {0, 0, 0, 0};
// watchdog counter
volatile u08 ticksSinceLastCommand = 0;

void startEscSignaling();

#define MAX_CHECKS 10

// 1 enables the manual inc/dec power buttons on PINC bit 4 and 5
volatile u08 buttonsEnabled = 0;
volatile uint16_t down5count = 0;
volatile uint16_t down4count = 0;

typedef struct {
	uint8_t state[MAX_CHECKS];
	uint8_t ix;
	volatile uint8_t debounced_state;
	volatile uint8_t changed;
} DebouncerData;
DebouncerData cDebouncerData;

// button handling

void incSpeed() {
    if(speed[0] < 100){
        speed[0] += 10;
    }
    putdu16(speed[0]);
}

void decSpeed() {
    if(speed[0] > 0){
        speed[0] -= 10;
    }
    putdu16(speed[0]);
}

void debounce(uint8_t inbyte, DebouncerData* debounceData) {
	debounceData->state[debounceData->ix++] = inbyte;
	if (debounceData->ix >= MAX_CHECKS) {
		debounceData->ix = 0;
	}
	uint8_t j = 0xff;
	for (uint8_t i = 0; i < MAX_CHECKS; i++) {
		j &= debounceData->state[i];
	}
	debounceData->changed = (debounceData->debounced_state ^ j) & j;
	debounceData->debounced_state = j;
}

volatile u08 motorNo = 0;

// turns on signal for motor motorNo, and sets timer 1 to 1-2ms according to speed[motorNo]
void startEscSignaling() {
	motorNo = 0;
	PORTB |= 1 << motorNo;
	OCR1A = TIMER_1MS + speed[motorNo] * SPEED_TIMER_MULT;
	TIFR1 |= 1 << OCF1A;	// clear interrupt flag (sic!)
	TCNT1 = 0;
    TIMSK1 |= 1<<OCIE1A;  // Enable Interrupt TimerCounter1 Compare Match A (SIG_OUTPUT_COMPARE1A)
}

// called at end of pulse for motor motorNo
// turns off pulse for this motor and starts timer for next motor
ISR(SIG_OUTPUT_COMPARE1A) {
	PORTB &= ~(1 << motorNo);
	motorNo++;
	if (motorNo < MOTOR_COUNT) {
		PORTB |= 1 << motorNo;
		OCR1A = TIMER_1MS + speed[motorNo] * SPEED_TIMER_MULT;	// 1ms + 1ms * speed / MAX_SPEED
	} else {
		TIMSK1 &= ~(1 << OCIE1A);	// disable this interrupt
	}
	TCNT1 = 0;
}

// each 1ms
ISR(SIG_OUTPUT_COMPARE0A) {
	if (escSignalPhase == 20) {
		escSignalPhase = 0;
		startEscSignaling();

		if (ticksSinceLastCommand > MAX_TICKS_BETWEEN_COMMANDS) {
			for (u08 i = 0; i < MOTOR_COUNT; i++) {
				speed[i] = 0;
			}
		} else {
			ticksSinceLastCommand++;
		}
	} else {
		escSignalPhase++;
	}

	if (buttonsEnabled) {
		debounce(PINC, &cDebouncerData);
		// check just pushed buttons
		if (cDebouncerData.changed & 1<<5) {
			incSpeed();
		} else if (cDebouncerData.changed & 1<<4) {
			decSpeed();
		}

		// check for held down buttons
		if (cDebouncerData.debounced_state & (1<<5)) {
			if (down5count > 500) {
				incSpeed();
				down5count -= 80;
			}
			down5count++;
		} else {
			down5count = 0;
		}

		if (cDebouncerData.debounced_state & 1<<4) {
			if (down4count > 500) {
				decSpeed();
				down4count -= 80;
			}
			down4count++;
		} else {
			down4count = 0;
		}

		OCR1B = TIMER_1MS + speed[0] * SPEED_TIMER_MULT;
	}

	TCNT0 = 0;	// reset to get interrupt again
}

int main(void) {
	// setup timer 1 in CTC mode, SIG_OUTPUT_COMPARE0A after a calculated interval
    TCCR1A = 0;  			// Mode CTC
    TCCR1B = 1<<WGM12 | 1<<CS11;   // mode CTC OCR1A, Clock/8 = 2.5MHz (2500 steps per ms)
    OCR1A = 50000;          // 20MHz / 8 / 50Hz = 50000

    // set up timer 0 to generate compare match interrupt each 1ms
    TIMSK0 = 1<<OCIE0A;  // Enable Interrupt TimerCounter0 Compare Match A (SIG_OUTPUT_COMPARE0A)
    TCCR0A = 1<<WGM01;  // Mode = CTC
    TCCR0B = 1<<CS02;   // Clock/256 = 78125Hz
    OCR0A = 78;          // 78125 / 78 = 1001.6Hz SIG_OUTPUT_COMPARE0A interrupts

    DDRB = 0b00001111;	// esc signal 0-3

    uartInit(10); // 129 for 9600bps at 20MHz clock, 10 for 115200

    sei();
    while (1) {
    	if (lineAvail()) {
    		u08 ch = getch();
    		if (ch == 'p') {
    			// set power command for motor 0-3
    			// "pxx xx xx xx\r"
    			buttonsEnabled = 0;

    			for (u08 i = 0; i < MOTOR_COUNT; i++) {
					u16 power = getds16();
					if (power < 256) {
						if (power > POWER_LIMIT) {
							power = POWER_LIMIT;
						}
						speed[i] = power;
						ch = getch();
						if (ch == '\r') {
							ungetch(ch);
							break;
						}
					}
    			}
    			ticksSinceLastCommand = 0;
    		}
    		while (getch() != '\r') {}
    	}
    }
}
