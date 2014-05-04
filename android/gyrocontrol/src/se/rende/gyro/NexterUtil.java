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

import java.util.Arrays;

public class NexterUtil {

	/**
	 * Return a value guaranteed to be within bounds.
	 * @param value
	 * @param lowerBound
	 * @param upperBound
	 * @return lowerBound if value < lowerBound, uppBound if value > upperBound, else value
	 */
	public static double constrain(double value, double lowerBound,
			double upperBound) {
		if (value < lowerBound) {
			return lowerBound;
		} else if (value > upperBound) {
			return upperBound;
		} else {
			return value;
		}
	}

	public static class Smoother {
		private double smoothFactor;
		private double previousData;

		public Smoother(double smoothFactor) {
			this.smoothFactor = smoothFactor;
		}

		double filter(double currentData) {
			if (smoothFactor != 1f) // only apply time compensated filter if
									// smoothFactor is applied
				return (previousData * (1f - smoothFactor) + (currentData * smoothFactor));
			else
				return currentData; // if smoothFactor == 1.0, do not calculate,
									// just bypass!
		}
	}

	public static double calculateMedian(double[] fs) {
		Arrays.sort(fs);
		return fs[fs.length / 2];
	}


}
