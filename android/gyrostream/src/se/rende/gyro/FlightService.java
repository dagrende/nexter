package se.rende.gyro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import se.rende.gyro.BluetoothService.BluetoothServiceState;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
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
	private static final double ACCEL_SMOOTH_FACTOR = 1f;
	private static final int THROW_AWAY_SAMPLES = 100;
	private static final int CALIBATE_VALUE_COUNT = 49;
	private static final double ATTITUDE_SCALING = (double)Math.PI / 4 / 100;
	private static final double MAXOUTPUT = 50;
	private FlightAngle flightAngle = new FlightAngleARG();
	private PIDdata accelPID[] = new PIDdata[3];
	private PIDdata stickPID[] = new PIDdata[3];
	private PIDdata gyroPID[] = new PIDdata[3];
	private BluetoothService bluetoothService;
	private SensorManager sensorManager;
	private Sensor gyroscope;
	private double accel[] = new double[3];
	private double accelZero[] = new double[3];
	private double gyro[] = new double[3];
	private double gyroZero[] = new double[3];
	private NexterUtil.Smoother[] accelFilter = {
			new NexterUtil.Smoother(ACCEL_SMOOTH_FACTOR),
			new NexterUtil.Smoother(ACCEL_SMOOTH_FACTOR),
			new NexterUtil.Smoother(ACCEL_SMOOTH_FACTOR) };
	private double accelCalibrateValues[][] = { new double[CALIBATE_VALUE_COUNT],
			new double[CALIBATE_VALUE_COUNT], new double[CALIBATE_VALUE_COUNT] };
	private int accelCalibrateIndex = 0;
	private double gyroCalibrateValues[][] = { new double[CALIBATE_VALUE_COUNT],
			new double[CALIBATE_VALUE_COUNT], new double[CALIBATE_VALUE_COUNT] };
	private int gyroCalibrateIndex = 0;
	private double power[] = new double[4];
	private Sensor accelerometer;
	private StickValues sticks;
	private MySensorEventListener sensorEventListener = new MySensorEventListener();
	private List<AngleListener> angleListeners = new ArrayList<FlightService.AngleListener>();
	private boolean armed = false;
	private SharedPreferences prefs;
	private String[][] propNameDefaults = {
			{"gp", "0.8"}, 
			{"gi", "0"}, 
			{"gd", "150"}, 
			{"gw", "1000"}, 
			{"sp", "0"}, 
			{"si", "0"}, 
			{"sd", "0"}, 
			{"sw", "0.375"}};

	private enum Mode {
		CALIBRATION, FLIGHT
	};

	public FlightService(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

		sensorManager = (SensorManager) context
				.getSystemService(Context.SENSOR_SERVICE);
		
		for (int i = 0; i < stickPID.length; i++) {
			stickPID[i] = new PIDdata();
			gyroPID[i] = new PIDdata();
		}
		
		setAllFromPrefs();

		// PID[HEADING].P = 3.0f;
		// PID[HEADING].I = 0.1f;
		// PID[HEADING].D = 0.0f;		
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
		
		gyroPID[0].integratedError = 0;
		gyroPID[1].integratedError = 0;
	}

	public void stop() {
		Log.d("FlightService", "stop"); // qwe
		sensorManager.unregisterListener(sensorEventListener, gyroscope);
		sensorManager.unregisterListener(sensorEventListener, accelerometer);

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
					logArray("accelZero", accelZero);
					logArray("gyroZero", gyroZero);
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
						
						processFlightControl(flightAngle.getAngles(), dT);
						if ((event.timestamp - lastLogTime) * NS2S > .2) {
//							logArray("angles", flightAngle.getAngles());
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

	public class PIDdata {
		double p;
		double i;
		double d;
		double o = 1;
		double prevError = 0;
		// AKA experiments with PID
		// double previousPIDTime;
		boolean firstPass = true;
		double integratedError = 0;
		double windupGuard; // Thinking about having individual wind up guards
							// for each PID
		double iState = 0;
		public double dState;
		
		@Override
		public String toString() {
			return "PIDdata(" + p + ", " + i + ", " + d + ")";
		}
	} // PID

	public interface AngleListener {
		void angleChanged(double pitch, double roll, double yaw);
	}

	protected void processFlightControl(double[] angles, double dT) {
//		double pitchAttitudeCmd = updatePID(sticks.forward * ATTITUDE_SCALING,
//				-flightAngle.getAngle(PITCH), stickPID[PITCH], dT, "stickPID[PITCH]");
//		double rollAttitudeCmd = updatePID(sticks.right * ATTITUDE_SCALING,
//				flightAngle.getAngle(ROLL), stickPID[ROLL], dT, "stickPID[ROLL]");

		double pitchAttitudeCmd = sticks.forward * ATTITUDE_SCALING;
		double rollAttitudeCmd = sticks.right * ATTITUDE_SCALING;
		
		double pitchForce = updatePID(pitchAttitudeCmd, -angles[PITCH], gyroPID[PITCH], dT, "gyroPID[PITCH]");
		double rollForce = updatePID(rollAttitudeCmd, angles[ROLL], gyroPID[ROLL],	dT, "gyroPID[ROLL]");

//		if (lastTime - System.currentTimeMillis() < -200l) {
//			Log.d("pos", "ierr=" + gyroPID[PITCH].integratedError);
//			lastTime = System.currentTimeMillis();
//		}
		// processHeading();

		double yawForce = 0;

		// pitch v up from horizontal
		// roll v right from horizontal
		// 3 2
		// 1 0
		power[3] = sticks.up - pitchForce + rollForce - yawForce;
		power[2] = sticks.up - pitchForce - rollForce + yawForce;
		power[1] = sticks.up + pitchForce + rollForce + yawForce;
		power[0] = sticks.up + pitchForce - rollForce - yawForce;

		// processMinMaxMotorCommand();
		
		if (sticks.up > 0f) {
			// limit 
			for (int i = 0; i < power.length; i++) {
				power[i] = Math.max(0f, Math.min(255f, power[i]));
			}
		} else {
			// turn off motors unconditionally when no up thrust
			for (int i = 0; i < power.length; i++) {
				power[i] = 0f;
			}
		}
		String message = "p" + (int) (power[0]) + " " + (int) (power[1]) + " "
				+ (int) (power[2]) + " " + (int) (power[3]) + "\r";
		if (bluetoothService != null && bluetoothService.getState() == BluetoothServiceState.CONNECTED && isArmed()) {
			// send power settings command to motors
			bluetoothService.write(message.getBytes());
		}
		
		// log power settings command
//		Log.d("roll-pid", String.format("roll a %10.3f p %10.3f", angles[ROLL], power[0]));
	}

	long lastTime = 0;
	/**
	 * do one pid calculation step
	 * @param targetPosition
	 * @param currentPosition
	 * @param pidData
	 * @param dt
	 * @param label
	 * @return
	 */
	private double updatePIDAVRPID(double targetPosition, double currentPosition, PIDdata pidData, double dt, String label) {
//		Log.d("pid", pidData.toString());
		
	    double Perror = targetPosition - currentPosition; 
	    double output = (pidData.p * Perror + pidData.i * pidData.integratedError) / pidData.o 
	    		+ pidData.d * (Perror - pidData.prevError);
	    pidData.prevError = Perror; 
	    pidData.integratedError += Perror; 
	  
	    // Accumulate Integral error *or* Limit output. 
	    // Stop accumulating when output saturates 
	    if (output > MAXOUTPUT) {
	    	Log.d("pid", String.format("op=%.3d tp=%.3d cp=%.3d dt=%d", output, targetPosition, currentPosition, dt));
	        output = MAXOUTPUT; 
	    } else if (output < -MAXOUTPUT) {
	    	Log.d("pid", String.format("op=%.3d tp=%.3d cp=%.3d dt=%d", output, targetPosition, currentPosition, dt));
	        output = -MAXOUTPUT; 
	    }
	    return output; 
	}
	
	/**
	 * PID step slightly modified code from http://www.eetimes.com/design/embedded/4211211/PID-without-a-PhD.
	 * @param targetPosition
	 * @param currentPosition
	 * @param pid
	 * @param dt
	 * @param label
	 * @return
	 */
	double updatePIDEE(double targetPosition, double currentPosition, PIDdata pid, double dt, String label) {
		double error = targetPosition - currentPosition;
		double pTerm = pid.p * error;
		// calculate the proportional term
		// calculate the integral state with appropriate limiting
		pid.integratedError += error;
		if (pid.integratedError > pid.windupGuard) {
			pid.integratedError = pid.windupGuard;
		} else if (pid.integratedError < -pid.windupGuard) {
			pid.integratedError = -pid.windupGuard;
		}
		double iTerm = pid.i * pid.integratedError; // calculate the integral term
		double dTerm = pid.d * (currentPosition - pid.dState);
		pid.dState = currentPosition;
		return pTerm + iTerm - dTerm;
	}
	
	/**
	 * After http://en.wikipedia.org/wiki/PID_controller.
	 * @param targetPosition
	 * @param currentPosition
	 * @param pid
	 * @param dt
	 * @param label
	 * @return
	 */
	double updatePID(double targetPosition, double currentPosition, PIDdata pid, double dt, String label) {
		  double error = targetPosition - currentPosition;
		  pid.integratedError += error * dt;
		  if (pid.integratedError > pid.windupGuard) {
			  pid.integratedError = pid.windupGuard;
		  } else if (pid.integratedError < -pid.windupGuard) {
			  pid.integratedError = -pid.windupGuard;
		  }
		  double derivative = (error - pid.prevError) / dt;
		  pid.prevError = error;
		  return (pid.p * error) + (pid.i * pid.integratedError) + (pid.d * derivative);
	}

//	previous_error = setpoint - actual_position
//			integral = 0
//			start:
//			  error = setpoint - actual_position
//			  integral = integral + (error*dt)
//			  derivative = (error - previous_error)/dt
//			  output = (Kp*error) + (Ki*integral) + (Kd*derivative)
//			  previous_error = error
//			  wait(dt)
//			  goto start

	// Modified from
	// http://www.arduino.cc/playground/Main/BarebonesPIDForEspresso
//	public double updatePID(double targetPosition, double currentPosition, PIDdata pidData, double dt, String label) {
//		double error = targetPosition - currentPosition;
//		if (pidData.firstPass) { // AKA PID experiments
//			pidData.firstPass = false;
//			pidData.lastPosition = currentPosition;
//			error = NexterUtil.constrain(error, -pidData.windupGuard, pidData.windupGuard);
//		} else {
//			pidData.integratedError += error * dt;
//			pidData.integratedError = NexterUtil.constrain(pidData.integratedError, -pidData.windupGuard, pidData.windupGuard);
//			double dTerm = pidData.d	* (currentPosition - pidData.lastPosition) / (dt * 100);
//			pidData.lastPosition = currentPosition;
//			error = pidData.p * error + pidData.i * pidData.integratedError + dTerm;
//		}
////		Log.d("FlightService", label + ": " + error + "=updatePID(" + targetPosition + ", " + currentPosition + ", " + pidData + ", " + dt + ")");
//		return error;
//	}
	
	protected void logArray(String label, double[] a) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(a[i]);
		}
		Log.d("FlightService", label + "=(" + sb + ")");
	}

	
	public double[] getPower() {
		return power;
	}

	// void zeroIntegralError(long currentTime) {
	// for (byte axis = ROLL; axis < LASTLEVELAXIS; axis++) {
	// PID[axis].integratedError = 0;
	// PID[axis].previousPIDTime = currentTime;
	// }
	// }

	public void setBluetoothService(BluetoothService bluetoothService) {
		this.bluetoothService = bluetoothService;
	}

	public void setSticks(StickValues sticks) {
		this.sticks = sticks;
	}

	public static class StickValues {
		double up;
		double turnCw;
		double forward;
		double right;

		@Override
		public String toString() {
			return "sticks(" + up + ", " + turnCw + ", " + forward + ", "
					+ right + ")";
		}
	}

	public void addAngleListener(AngleListener angleListener) {
		angleListeners .add(angleListener);
	}

	/**
	 * If cmd is a valid command for this component, execute it and return true.
	 * @param cmd the command with parameters
	 * @return true if command is recognized by this component (regardless of success), else false
	 */
	public boolean executeCommand(String cmd) {
		Log.d("FlightServiceSet", cmd);
		boolean cmdTaken = true;
		StringTokenizer st = new StringTokenizer(cmd, " \t\r\n");
		String verb = st.nextToken();
		if ("set".equals(verb)) {
			String propName = st.nextToken();
			String value = st.nextToken();
			cmdTaken = setProperty(propName, value);
			
			if (cmdTaken) {
				// save setting
				Editor edit = prefs.edit();
				edit.putString(propName, value);
				edit.commit();
			}
		} else if ("arm".equals(verb)) {
			setArmed(true);
		} else if ("disarm".equals(verb)) {
			setArmed(false);
			if (bluetoothService != null && bluetoothService.getState() == BluetoothServiceState.CONNECTED) {
				// turn off all four motors
				bluetoothService.write("p0 0 0 0\r".getBytes());
			}
		} else {
			cmdTaken = false;
		}
		return cmdTaken;
	}

	/**
	 * load parameters from android prefs or propNameDefaults.
	 */
	public void setAllFromPrefs() {		
		for (String[] propNameDefault : propNameDefaults) {
			setProperty(propNameDefault[0], prefs.getString(propNameDefault[0], propNameDefault[1]));
		}
	}
	
	/**
	 * Returns a string with property name value\n for each property in propNameDefaults.
	 * @return
	 */
	public String getAllProps() {
		StringBuilder sb = new StringBuilder();
		for (String[] propNameDefault : propNameDefaults) {
			sb.append("property " + propNameDefault[0] + " " + getPropAsString(propNameDefault[0]) + "\n");
		}
		return sb.toString();
	}

	/**
	 * Sets property to value, and return true if the property exists and was set.
	 * @param propName
	 * @param value
	 * @return true if set, false if not a valid property
	 * @throws NumberFormatException if value is not parsable to the property datatype
	 */
	private boolean setProperty(String propName, String value) {
		if ("gp".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			gyroPID[0].p = doubleValue;
			gyroPID[1].p = doubleValue;
		} else if ("gi".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			gyroPID[0].i = doubleValue;
			gyroPID[1].i = doubleValue;
		} else if ("gd".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			gyroPID[0].d = doubleValue;
			gyroPID[1].d = doubleValue;
		} else if ("gw".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			gyroPID[0].windupGuard = doubleValue;
			gyroPID[1].windupGuard = doubleValue;
		} else if ("sp".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			stickPID[0].p = doubleValue;
			stickPID[1].p = doubleValue;
		} else if ("si".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			stickPID[0].i = doubleValue;
			stickPID[1].i = doubleValue;
		} else if ("sd".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			stickPID[0].d = doubleValue;
			stickPID[1].d = doubleValue;
		} else if ("sw".equals(propName)) {
			double doubleValue = Double.parseDouble(value);
			stickPID[0].windupGuard = doubleValue;
			stickPID[1].windupGuard = doubleValue;
		} else {
			return false;
		}
		return true;
	}
	
	/**
	 * Returns the specified property converted to string format.
	 * @param propName the property to get
	 * @return the value string, or null if not found
	 */
	public String getPropAsString(String propName) {
		if ("gp".equals(propName)) {
			return Double.toString(gyroPID[0].p);
		} else if ("gi".equals(propName)) {
			return Double.toString(gyroPID[1].i);
		} else if ("gd".equals(propName)) {
			return Double.toString(gyroPID[1].d);
		} else if ("gw".equals(propName)) {
			return Double.toString(gyroPID[1].windupGuard);
		} else if ("sp".equals(propName)) {
			return Double.toString(stickPID[1].p);
		} else if ("si".equals(propName)) {
			return Double.toString(stickPID[1].i);
		} else if ("sd".equals(propName)) {
			return Double.toString(stickPID[1].d);
		} else if ("sw".equals(propName)) {
			return Double.toString(stickPID[1].windupGuard);
		} else {
			return null;
		}
	}

	public void setArmed(boolean armed) {
		this.armed = armed;
	}

	public boolean isArmed() {
		return armed;
	}

}
