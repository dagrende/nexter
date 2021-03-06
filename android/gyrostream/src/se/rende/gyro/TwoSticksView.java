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
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

public class TwoSticksView extends View {
	private boolean manualMode = true;
	private float radius = 40f;
	private List<StickChangeListener> listeners = new ArrayList<TwoSticksView.StickChangeListener>();
	private StickInfo[] stick = {new StickInfo(), new StickInfo()};
	
	public TwoSticksView(Context context) {
		super(context);
	}
	
	public void addStickChangeListener(StickChangeListener listener) {
		listeners.add(listener);
	}
	
	public void removeStickChangeListener(StickChangeListener listener) {
		listeners.remove(listener);
	}
	
	/**
	 * True if a point is on the stick.
	 * @param id stick id 0 or 1
	 * @param x
	 * @param y
	 * @return stick id if x, y is on stick, else -1
	 */
	private int isOnStick(float x, float y) {
		for (int id = 0; id < 2; id++) {
			float dx = stick[id].center.x - x;
			float dy = stick[id].center.y - y;
			if ((dx * dx + dy * dy) < radius * radius) {
				return id;
			}
		}
		return -1;
	}
	
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (manualMode) {
			int action = event.getActionMasked();
			int pn = event.getPointerCount();
			if (action == MotionEvent.ACTION_DOWN
					|| action == MotionEvent.ACTION_POINTER_DOWN) {
				for (int i = 0; i < pn; i++) {
					int pointerId = event.getPointerId(i);
					//				Log.d("TwoSticksView", "down i=" + i + " pid=" + pointerId);
					int stickId = isOnStick(event.getX(i), event.getY(i));
					if (stickId != -1) {
						stick[stickId].dragging = true;
						stick[stickId].pointerId = pointerId;
						//					Log.d("TwoSticksView", "  stickId=" + stickId);
					}
				}
			} else if (action == MotionEvent.ACTION_MOVE) {
				for (int i = 0; i < pn; i++) {
					int pointerId = event.getPointerId(i);
					for (int stickId = 0; stickId < 2; stickId++) {
						StickInfo stickInfo = stick[stickId];
						if (stickInfo.pointerId == pointerId
								&& stickInfo.dragging) {
							//						Log.d("TwoSticksView", "move i=" + i + " pid=" + pointerId + " stickId=" + stickId);
							stickInfo.pos.x = event.getX(i)
									- stickInfo.center.x;
							stickInfo.pos.y = event.getY(i)
									- stickInfo.center.y;
							invalidateStick(stickId, stickInfo.lastPos.x,
									stickInfo.lastPos.y, stickInfo.pos.x,
									stickInfo.pos.y);
							stickInfo.lastPos.x = stickInfo.pos.x;
							stickInfo.lastPos.y = stickInfo.pos.y;
							fireChangeStickEvent(stickId, stickInfo.pos.x
									/ (getWidth() / 4f), -stickInfo.pos.y
									/ (getHeight() / 2f));
						}
					}
				}
			} else if (action == MotionEvent.ACTION_UP
					|| action == MotionEvent.ACTION_POINTER_UP) {
				for (int i = 0; i < pn; i++) {
					int pointerId = event.getPointerId(i);
					for (int stickId = 0; stickId < 2; stickId++) {
						StickInfo stickInfo = stick[stickId];
						if (stickInfo.pointerId == pointerId
								&& stickInfo.dragging) {
							//						Log.d("TwoSticksView", "up i=" + i + " pid=" + pointerId + " stickId=" + stickId);
							stickInfo.pointerId = -1;
							stickInfo.dragging = false;
							stickInfo.lastPos.x = stickInfo.pos.x;
							stickInfo.lastPos.y = stickInfo.pos.y;
							stickInfo.pos.x = 0;
							stickInfo.pos.y = 0;
							invalidate();
							fireChangeStickEvent(stickId, 0, 0);
						}
					}
				}
			}
			return true;
		} else {
        	return false;
        }
    }

    private void fireChangeStickEvent(int id, float x, float y) {
		for (StickChangeListener listener : listeners) {
			listener.changedStick(id, x, y);
		}
	}

	private void invalidateStick(int stickId, float x, float y, float x2, float y2) {
//    	Log.d("TwoSticks", "invalidateStick(" + x + ", " + y + ")");
    	float r = radius + 5f;
		float left = Math.min(stick[stickId].center.x + x - r, stick[stickId].center.x + x2 - r);
		float top = Math.min(stick[stickId].center.y + y - r, stick[stickId].center.y + y2 - r);
		float right = Math.max(stick[stickId].center.x + x + r, stick[stickId].center.x + x2 + r);
		float bottom = Math.max(stick[stickId].center.y + y + r, stick[stickId].center.y + y2 + r);
//		Log.d("TwoSticks", "invalidate(" + left + ", " + top + ", " + right + ", " + bottom + ")");
		invalidate((int)left, (int)top, (int)right, (int)bottom);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		Paint paint = new Paint();
		paint.setColor(Color.WHITE);
		
		// left stick
		if (stick[0].center.x == 0) {
			stick[0].center.x = getWidth() / 4f;
			stick[0].center.y = getHeight() / 2f;
			stick[1].center.x = getWidth() * 3f / 4f;
			stick[1].center.y = getHeight() / 2f;
		}
//		Log.d("TwoSticks", "drawCircle(" + stickCenterX + stick[id].pos.x + " " + stickCenterY + stick[id].pos.y + ")");
		canvas.drawCircle(stick[0].center.x + stick[0].pos.x, stick[0].center.y + stick[0].pos.y, radius, paint);
		
		// right stick
		canvas.drawCircle(stick[1].center.x + stick[1].pos.x, stick[1].center.y + stick[1].pos.y, radius, paint);
	}

	public void setSticks(float stick0x, float stick0y, float stick1x, float stick1y) {
		if (manualMode) {
			throw new IllegalArgumentException("wrong mode - setSticks can not be used in manual mode");
		} else {
			stick[0].pos.x = stick0x * (getWidth() / 4f);
			stick[0].pos.y = stick0y * (getHeight() / 2f);
			stick[1].pos.x = stick1x * (getWidth() / 4f);
			stick[1].pos.y = stick1y * (getHeight() / 2f);
			invalidateStick(0, stick[0].lastPos.x,
					stick[0].lastPos.y, stick[0].pos.x,
					stick[0].pos.y);
			invalidateStick(1, stick[1].lastPos.x,
					stick[1].lastPos.y, stick[1].pos.x,
					stick[1].pos.y);
			stick[0].lastPos.set(stick[0].pos);
			stick[1].lastPos.set(stick[1].pos);
		}
	}

	public interface StickChangeListener {
		/**
		 * Called when stick has changed.
		 * @param id 0 for left stick 1 for right
		 * @param x -1.0 to 1.0 for left to right
		 * @param y -1.0 to 1.0 for bottom to top
		 * @param y2 
		 */
		void changedStick(int stickId, float x, float y);
	}
	
	public class StickInfo {
		PointF center = new PointF();
		PointF pos = new PointF();
		PointF lastPos = new PointF();
		boolean dragging = false;
		public int pointerId;
	}

	public boolean isManualMode() {
		return manualMode;
	}

	public void setManualMode(boolean manualMode) {
		this.manualMode = manualMode;
	}


}
