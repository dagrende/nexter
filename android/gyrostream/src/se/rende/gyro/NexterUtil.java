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
