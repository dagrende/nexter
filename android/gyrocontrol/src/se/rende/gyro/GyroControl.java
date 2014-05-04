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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Toast;

public class GyroControl extends Activity {
	private GyroStreamServerClient gyroServer;
	private FlightService flightService;
	private GlobeView globeView;
	private SeekBar upSeekBar;
	private double up = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.manual);
		
		CheckBox onCheckBox = (CheckBox) findViewById(R.id.onCheckBox);
		onCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			   public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
				   if (isChecked) {
					   flightService.start();
				   } else {
					   flightService.stop();
				   }
			   }
			});
		
		globeView = (GlobeView) findViewById(R.id.globeView);
		
        flightService = new FlightService(this);
        flightService.addAngleListener(new FlightService.AngleListener() {
        	public void angleChanged(final double pitch, final double roll, final double yaw) {
        		if (globeView != null) {
	        		globeView.post(new Runnable() {
	    				public void run() {
	    					globeView.setAngles(-pitch, roll, 0 * yaw);
	    					gyroServer.writeLine(String.format("s %.2f %.2f %.2f %.2f", up, -pitch, roll, 0 * yaw));
	    				}
	        		});
        		}
        	};
        });

        upSeekBar = (SeekBar) findViewById(R.id.upSeekBar);
		upSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
	        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	        	up = seekBar.getProgress();
	        }
	        public void onStartTrackingTouch(SeekBar seekBar) {}
	        public void onStopTrackingTouch(SeekBar seekBar) {
	        }
	    });
		        
        gyroServer = new GyroStreamServerClient(gyroServerHandler, "192.168.43.1", 8081);
    	gyroServer.connect();


		handleOnePIDParam(R.id.pSeekBar, "gp", 100f, 0.8f);
		handleOnePIDParam(R.id.iSeekBar, "gi", 100f, 0);
		handleOnePIDParam(R.id.dSeekBar, "gd", 500f, 150);
		handleOnePIDParam(R.id.wSeekBar, "gw", 5000f, 1000);

	}
        
	private void handleOnePIDParam(int id, final String propName, final float maxValue, final float initialValue) {
		final float factor = maxValue / 1000f;
		final SeekBar seekBar = (SeekBar) findViewById(id);
		seekBar.setMax(1000);
		int initialProgress = (int) (initialValue / factor);
		seekBar.setProgress(initialProgress);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
	        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	        }
	        public void onStartTrackingTouch(SeekBar seekBar) {}
	        public void onStopTrackingTouch(SeekBar seekBar) {
	        	gyroServer.writeLine("set " + propName + " " + Float.toString(seekBar.getProgress() * factor));
	        }
	    });
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.connect:
        	gyroServer.connect();
        	return true;
        case R.id.disconnect:
        	gyroServer.disconnect();
        	return true;
        }
        return false;
    }

	
    @Override
	protected void onStart() {
    	super.onStart();
	}
    
    @Override
    protected void onStop() {
    	super.onStop();
    	flightService.stop();
    }
	
	private final Handler gyroServerHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case GyroStreamServerClient.MESSAGE_RECEIVED_LINE:
//            	handleGyroStreamCommand(msg.getData().getString("message"));
            	break;
            case GyroStreamServerClient.MESSAGE_CONNECT:
                Toast.makeText(GyroControl.this, "connected", Toast.LENGTH_LONG).show();
            	break;
            case GyroStreamServerClient.MESSAGE_DISCONNECT:
                Toast.makeText(GyroControl.this, "disconnected", Toast.LENGTH_LONG).show();
            	break;
            case GyroStreamServerClient.MESSAGE_ERROR:
                Toast.makeText(GyroControl.this, "connection error " + msg.getData().getString("message"), Toast.LENGTH_LONG).show();
            	break;
            }
		}

    };
}