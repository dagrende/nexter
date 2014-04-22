package se.rende.gyro;

public class FlightAngleARG implements FlightAngle {
	private static final int PITCH = 0;
	private static final int ROLL = 1;
	private static final int YAW = 2;
	double Kp; // proportional gain governs rate of convergence to accelerometer/magnetometer
	double Ki; // integral gain governs rate of convergence of gyroscope biases
	double halfT; // half the sample period
	double q0, q1, q2, q3; // quaternion elements representing the estimated orientation
	double exInt, eyInt, ezInt; // scaled integral error

	private double[] angle = new double[3];

	public FlightAngleARG() {
		init();
	}

	public void init() {
		q0 = 1.0f;
		q1 = 0.0f;
		q2 = 0.0f;
		q3 = 0.0f;
		exInt = 0.0f;
		eyInt = 0.0f;
		ezInt = 0.0f;

		Kp = 0.2f; // 2.0;
		Ki = 0.0005f; // 0.005;
		
		for (int i = 0; i < angle.length; i++) {
			angle[i] = 0;
		}
	}

	void argUpdate(double gx, double gy, double gz, double ax, double ay, double az,
			double G_Dt) {
		double norm;
		double vx, vy, vz;
		double ex, ey, ez;

		halfT = G_Dt / 2;

		// normalise the measurements
		norm = (double) Math.sqrt(ax * ax + ay * ay + az * az);
		ax = ax / norm;
		ay = ay / norm;
		az = az / norm;

		// estimated direction of gravity and flux (v and w)
		vx = 2 * (q1 * q3 - q0 * q2);
		vy = 2 * (q0 * q1 + q2 * q3);
		vz = q0 * q0 - q1 * q1 - q2 * q2 + q3 * q3;

		// error is sum of cross product between reference direction of fields
		// and direction measured by sensors
		ex = (vy * az - vz * ay);
		ey = (vz * ax - vx * az);
		ez = (vx * ay - vy * ax);

		// integral error scaled integral gain
		exInt = exInt + ex * Ki;
		eyInt = eyInt + ey * Ki;
		ezInt = ezInt + ez * Ki;

		// adjusted gyroscope measurements
		gx = gx + Kp * ex + exInt;
		gy = gy + Kp * ey + eyInt;
		gz = gz + Kp * ez + ezInt;

		// integrate quaternion rate
		double q0i = (-q1 * gx - q2 * gy - q3 * gz) * halfT;
		double q1i = (q0 * gx + q2 * gz - q3 * gy) * halfT;
		double q2i = (q0 * gy - q1 * gz + q3 * gx) * halfT;
		double q3i = (q0 * gz + q1 * gy - q2 * gx) * halfT;
		q0 += q0i;
		q1 += q1i;
		q2 += q2i;
		q3 += q3i;

		// normalise quaternion
		norm = (double) Math.sqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3);
		q0 = q0 / norm;
		q1 = q1 / norm;
		q2 = q2 / norm;
		q3 = q3 / norm;
	}

	void eulerAngles() {
		angle[ROLL] = (double) Math.atan2(2 * (q0 * q1 + q2 * q3), 1 - 2 * (q1
				* q1 + q2 * q2));
		angle[PITCH] = (double) Math.asin(2 * (q0 * q2 - q1 * q3));
		angle[YAW] = (double) Math.atan2(2 * (q0 * q3 + q1 * q2), 1 - 2 * (q2
				* q2 + q3 * q3));
	}

	public void calculate(double rollRate, double pitchRate, double yawRate,
			double longitudinalAccel, double lateralAccel, double verticalAccel,
			double G_Dt) {

		argUpdate(rollRate, pitchRate, yawRate, 
				longitudinalAccel, lateralAccel, verticalAccel, G_Dt);
		eulerAngles();
	}

	public double getAngle(int axis) {
		return angle[axis];
	}

	public double[] getAngles() {
		return angle;
	}

	


}
