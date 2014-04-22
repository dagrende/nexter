package se.rende.gyro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.View;

/**
 * Visualize the vehicle angles by a globe rotated by pitch and roll.
 * Visualize motors by four filled circles with radius proportional to power.
 * @author dag
 *
 */
public class GlobeView extends View {
	private float globeRadius = .8f;
	private float vehicleRadius = .03f;
	private float motorRadius = .1f;
	private int width;
	private int height;
	private int maxRadius;
	private Point center;
	private float[] motors = {0, 0, 0, 0};
	private float pitch;
	private float roll;
	private float yaw;
	private Paint whiteStrokePaint;
	private Paint redStrokePaint;
	private Paint whiteFillPaint;
	
	public GlobeView(Context context) {
		super(context);
		whiteStrokePaint = new Paint();
		whiteStrokePaint.setColor(Color.WHITE);
		whiteStrokePaint.setStyle(Style.STROKE);

		redStrokePaint = new Paint();
		redStrokePaint.setColor(Color.RED);
		redStrokePaint.setStyle(Style.STROKE);

		whiteFillPaint = new Paint();
		whiteFillPaint.setColor(Color.WHITE);
		whiteFillPaint.setStyle(Style.FILL);

		
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		// left stick
		if (width == 0) {
			width = getWidth();
			height = getHeight();
			center = new Point(width / 2, height / 2);
			maxRadius = Math.min(width, height) / 2;
		}

		float r = globeRadius * maxRadius / (float)Math.sqrt(2) + maxRadius * motorRadius;
		canvas.drawCircle(center.x + r, center.y + r, maxRadius * motorRadius * motors[0] / 100, whiteFillPaint);
		canvas.drawCircle(center.x - r, center.y + r, maxRadius * motorRadius * motors[1] / 100, whiteFillPaint);
		canvas.drawCircle(center.x + r, center.y - r, maxRadius * motorRadius * motors[2] / 100, whiteFillPaint);
		canvas.drawCircle(center.x - r, center.y - r, maxRadius * motorRadius * motors[3] / 100, whiteFillPaint);
		
		canvas.rotate(yaw * 180 / (float)Math.PI, center.x, center.y);
		r = maxRadius * globeRadius;
		canvas.drawCircle(center.x, center.y, r, whiteStrokePaint);
		
		drawAngle(canvas, r, pitch);
		
		canvas.rotate(90, center.x, center.y);
		drawAngle(canvas, r, roll);
		canvas.rotate(-90, center.x, center.y);
		canvas.drawCircle(center.x, center.y, maxRadius * vehicleRadius, whiteStrokePaint);
		canvas.rotate(-yaw * 180 / (float)Math.PI, center.x, center.y);
		
	}

	private void drawAngle(Canvas canvas, float r, float angle) {
		if (angle > Math.PI / 2) {
			angle -= Math.PI;
		} else if (angle < -Math.PI / 2) {
			angle += Math.PI;
		}
		if (angle > 0) {
			canvas.drawArc(
					new RectF(center.x - r, center.y - r
							* (float) Math.sin(angle), center.x + r, center.y
							+ r * (float) Math.sin(angle)), 0, 180, false,
					whiteStrokePaint);
		} else {
			canvas.drawArc(
					new RectF(center.x - r, center.y - r
							* (float) Math.sin(-angle), center.x + r, center.y
							+ r * (float) Math.sin(-angle)), 180, 180, false,
					whiteStrokePaint);
		}
	}

	public void setPower(double m0, double m1, double m2, double m3) {
		motors[0] = (float)m0;
		motors[1] = (float)m1;
		motors[2] = (float)m2;
		motors[3] = (float)m3;
		invalidate();
	}

	public void setAngles(double pitch, double roll, double yaw) {
		this.pitch = (float)pitch;
		this.roll = (float)roll;
		this.yaw = (float)yaw;		
		invalidate();
	}

}
