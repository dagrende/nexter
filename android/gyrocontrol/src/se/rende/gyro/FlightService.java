/*
 * Copyright (C) 2014 Dag Rende
 * 
 * Licensed under the GNU General Public License v3
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Dag Rende
 */

package se.rende.gyro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Responsible for and running all service controlling flight according to
 * commands from outside this service.
 * 
 * @author dag
 * 
 */
public class FlightService {
	private static final double NS2S = 1.0f / 1000000000.0f;
	private static final byte PITCH = 0;
	private static final byte ROLL = 1;
	private static final byte YAW = 2;
	private static final int XAXIS = 0;
	private static final int YAXIS = 1;
	private static final int ZAXIS = 2;
	private static final int THROW_AWAY_SAMPLES = 100;
	private static final int CALIBATE_VALUE_COUNT = 49;
	private FlightAngle flightAngle = new FlightAngleARG();
	private SensorManager sensorManager;
	private Sensor gyroscope;
	private double accel[] = new double[3];
	private double accelZero[] = new double[3];
	private double gyro[] = new double[3];
	private double gyroZero[] = new double[3];
	private double accelCalibrateValues[][] = { new double[CALIBATE_VALUE_COUNT],
			new double[CALIBATE_VALUE_COUNT], new double[CALIBATE_VALUE_COUNT] };
	private int accelCalibrateIndex = 0;
	private double gyroCalibrateValues[][] = { new double[CALIBATE_VALUE_COUNT],
			new double[CALIBATE_VALUE_COUNT], new double[CALIBATE_VALUE_COUNT] };
	private int gyroCalibrateIndex = 0;
	private Sensor accelerometer;
	private MySensorEventListener sensorEventListener = new MySensorEventListener();
	private List<AngleListener> angleListeners = new ArrayList<FlightService.AngleListener>();

	private enum Mode {
		CALIBRATION, FLIGHT
	};

	public FlightService(Context context) {
		sensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);
	}

	public void start() {
		Log.d("FlightService", "start");
		sensorEventListener.init();
		flightAngle.init();
		
		gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		sensorManager.registerListener(sensorEventListener, gyroscope,
				SensorManager.SENSOR_DELAY_GAME);	// 9.2ms period
		
		accelerometer = sensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(sensorEventListener, accelerometer,
				SensorManager.SENSOR_DELAY_GAME);	// 20ms period
		
	}

	public void stop() {
		Log.d("FlightService", "stop");
		sensorManager.unregisterListener(sensorEventListener, gyroscope);
		sensorManager.unregisterListener(sensorEventListener, accelerometer);

	}

	public void reset() {
		stop();
		start();
	}
	
	public class MySensorEventListener implements SensorEventListener {
		private long lastTime;
		Mode mode;
		boolean accelCalibrationReady;
		boolean gyroCalibrationReady;
		long lastLogTime;
		private int throwAwaySamplesLeft;
		
		public MySensorEventListener() {
			init();
		}
		
		private void init() {
			mode = Mode.CALIBRATION;
			accelCalibrationReady = false;
			gyroCalibrationReady = false;
			throwAwaySamplesLeft = THROW_AWAY_SAMPLES;
			accelCalibrateIndex = 0;
			gyroCalibrateIndex = 0;
		}
		
		public synchronized void onSensorChanged(SensorEvent event) {
			if (mode == Mode.CALIBRATION) {
				if (throwAwaySamplesLeft > 0) {
					throwAwaySamplesLeft--;
					return;
				}
				// calibration mode - collect some samples and calculate offsets from them
				if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
					if (accelCalibrateIndex < CALIBATE_VALUE_COUNT) {
						for (int i = 0; i < accelCalibrateValues.length; i++) {
							accelCalibrateValues[i][accelCalibrateIndex] = event.values[i];
						}
						accelCalibrateIndex++;
					} else {
						accelCalibrationReady = true;
					}
				} else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
					if (gyroCalibrateIndex < CALIBATE_VALUE_COUNT) {
						for (int i = 0; i < gyroCalibrateValues.length; i++) {
							gyroCalibrateValues[i][gyroCalibrateIndex] = event.values[i];
						}
						gyroCalibrateIndex++;
					} else {
						gyroCalibrationReady = true;
					}
				}
				if (accelCalibrationReady && gyroCalibrationReady) {
					for (int i = 0; i < gyroCalibrateValues.length; i++) {
						Arrays.sort(accelCalibrateValues[i]);
						accelZero[i] = accel[i] = accelCalibrateValues[i][accelCalibrateValues.length / 2];
						accelZero[ZAXIS] = 0f;
					}
					for (int i = 0; i < gyroCalibrateValues.length; i++) {
						Arrays.sort(gyroCalibrateValues[i]);
						gyroZero[i] = gyro[i] = gyroCalibrateValues[i][gyroCalibrateValues.length / 2];
					}
					mode = Mode.FLIGHT;
				}
			} else if (mode == Mode.FLIGHT) {
				// flight mode - calculate angles and control motors
				if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
					for (int i = 0; i < accel.length; i++) {
						accel[i] = event.values[i] - accelZero[i];
					}
				} else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
					if (lastTime != 0) {
						final double dT = (event.timestamp - lastTime) * NS2S;
						for (int i = 0; i < gyro.length; i++) {
							gyro[i] = event.values[i] - gyroZero[i];
						}

						flightAngle.calculate(
								gyro[ROLL], gyro[PITCH], gyro[YAW], 
								accel[YAXIS], accel[XAXIS], -accel[ZAXIS], dT);
						
						if ((event.timestamp - lastLogTime) * NS2S > .05) {
							for (AngleListener listener : angleListeners) {
								double[] angles = flightAngle.getAngles();
								listener.angleChanged(angles[0], angles[1], angles[2]);
							}
							lastLogTime = event.timestamp;
						}
					}
					lastTime = event.timestamp;
				}
			}
		}

		public void onAccuracyChanged(Sensor arg0, int arg1) {
		}
	};

	public interface AngleListener {
		void angleChanged(double pitch, double roll, double yaw);
	}

	public void addAngleListener(AngleListener angleListener) {
		angleListeners.add(angleListener);
	}
}
