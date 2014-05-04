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
