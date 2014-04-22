package se.rende.gyro;

public interface FlightAngle {
	void calculate(double rollRate, double pitchRate, double yawRate,
			double longitudinalAccel, double lateralAccel, double verticalAccel, double D_Dt);
	double getAngle(int axis);
	/**
	 * Returns a ref to an angle array
	 * @return array with pitch, roll, yaw
	 */
	double[] getAngles();
	void init();
}
