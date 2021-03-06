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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.StringTokenizer;

import se.rende.gyro.FlightService.StickValues;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class GyroStream extends Activity {
	private static final boolean D = false;
	private static final String TAG = "GyroStreamer";
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothService bluetoothService;
	private AndroidGyroStreamServer gyroServer;
    private FlightService.StickValues sticks = new StickValues();
	private FlightService flightService;
	private GlobeView globeView;
	private TextView btStatus;
	private SeekBar upSeekBar;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.manual);
		
		CheckBox armCheckbox = (CheckBox) findViewById(R.id.armCheckBox);
		armCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		   public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
			   if (isChecked) {
				   upSeekBar.setProgress(0);
			   }
			   flightService.setArmed(isChecked);
		   }
		});
		
		btStatus = (TextView) findViewById(R.id.btStatus);
		btStatus.setText("Not connected");
		
		CheckBox onCheckBox = (CheckBox) findViewById(R.id.onCheckBox);
		onCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			   public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
				   if (isChecked) {
					   flightService.start();
					   gyroServer.start();
				   } else {
					   flightService.stop();
				   }
			   }
			});
		
		globeView = (GlobeView) findViewById(R.id.globeView);
		
        flightService = new FlightService(this);
        flightService.setSticks(sticks);
        flightService.addAngleListener(new FlightService.AngleListener() {
        	public void angleChanged(final double pitch, final double roll, final double yaw) {
        		if (globeView != null) {
	        		globeView.post(new Runnable() {
	        			double[] power = flightService.getPower();
	    				public void run() {
	    					globeView.setAngles(-pitch, roll, 0 * yaw);
	    					globeView.setPower(power[0], power[1], power[2], power[3]);
	    				}
	        		});
        		}
        	};
        });

        upSeekBar = (SeekBar) findViewById(R.id.upSeekBar);
		flightService.addPropertyChangeListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if ("sticks".equals(event.getPropertyName())) {
					upSeekBar.setProgress((int) (((FlightService.StickValues) event.getNewValue()).up));
				}
			}
		});
		upSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
	        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	        	sticks.up = progress;
	        }
	        public void onStartTrackingTouch(SeekBar seekBar) {}
	        public void onStopTrackingTouch(SeekBar seekBar) {}
	    });
		
		bluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showState("Bt is not available");
        }
        
        
        gyroServer = new AndroidGyroStreamServer("nexter", gyroServerHandler);
    	gyroServer.start();


		handleOnePIDParam(R.id.pSeekBar, "gp", 100f, 0.8f);
		handleOnePIDParam(R.id.iSeekBar, "gi", 10f, 0);
		handleOnePIDParam(R.id.dSeekBar, "gd", 10f, 0);
		handleOnePIDParam(R.id.wSeekBar, "gw", 5000f, 1000);

	}
        
	private void handleOnePIDParam(int id, final String propName, final float maxValue, final float initialValue) {
		final float factor = maxValue / 1000f;
		final SeekBar seekBar = (SeekBar) findViewById(id);
		seekBar.setMax(1000);
		int initialProgress = (int) (initialValue / factor);
		seekBar.setProgress(initialProgress);
		flightService.addPropertyChangeListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (propName.equals(event.getPropertyName())) {
					seekBar.setProgress((int) (Float.parseFloat((String) event.getNewValue()) / factor));
				}
			}
		});
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			int setPropertyNesting = 0;
	        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	        	if (setPropertyNesting == 0) {
	        		setPropertyNesting++;
	        		flightService.setProperty(propName, Float.toString(progress * factor));
	        		setPropertyNesting--;
	        	}
	        }
	        public void onStartTrackingTouch(SeekBar seekBar) {}
	        public void onStopTrackingTouch(SeekBar seekBar) {}
	    });
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        menu.findItem(R.id.scan).setEnabled(bluetoothAdapter != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.stop:
        	gyroServer.stop();
        	flightService.stop();
        	if (bluetoothService != null) {
        		bluetoothService.stop();
        	}
        }
        return false;
    }

    private void handleGyroStreamCommand(String cmd) {
    	Log.d("GyroStream", "received: " + cmd);
    	StringTokenizer st = new StringTokenizer(cmd, " \n\r");
		if (cmd.startsWith("s ")) {
			// stick change command "s u t f r\n" where u, t, f and r if floats for up, turnCw, forward and right
			st.nextToken();
			sticks.up = Float.parseFloat(st.nextToken());
			sticks.forward = Float.parseFloat(st.nextToken());
			sticks.right = Float.parseFloat(st.nextToken());
			sticks.turnCw = Float.parseFloat(st.nextToken());
			
			Log.d("GyroStream", 
					String.format("up=%.2f forward=%.2f right=%.2f turnCw=%.2f", 
					sticks.up, sticks.forward, sticks.right, sticks.turnCw));			
			flightService.setSticks(sticks);
			
			
		} else if ("getProps".equals(cmd)) {
			gyroServer.writeLine(flightService.getAllProps());
		} else if (flightService.executeCommand(cmd)) {
			Toast.makeText(this, cmd, Toast.LENGTH_SHORT).show();
		}
    }
	
    @Override
	protected void onStart() {
		super.onStart();
		if (bluetoothAdapter != null) {
			if (bluetoothAdapter.isEnabled()) {
				if (bluetoothService == null) {
					setupBluetoothService();
				}
			} else {
				// start bluetooth and let onActivityResult setup the connection
				Intent enableIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
//				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			}
		}
	}
    
    @Override
    protected void onStop() {
    	super.onStop();
    	flightService.stop();
    }
	
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                bluetoothService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                setupBluetoothService();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

	private void setupBluetoothService() {
        bluetoothService = new BluetoothService(this, bluetoothHandler);
        flightService.setBluetoothService(bluetoothService);
	}
	
    // The Handler that gets information back from the BluetoothService
    private final Handler bluetoothHandler = new Handler() {
        private String mConnectedDeviceName;

		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case BluetoothService.MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (BluetoothService.BluetoothServiceState.values()[msg.arg1]) {
                case CONNECTED:
                    showState("Connected");
//                    mConversationArrayAdapter.clear();
                    break;
                case CONNECTING:
                    showState("Connecting...");
                    break;
                case LISTEN:
                case NONE:
                    showState("Not connected");
                    break;
                }
                break;
            case BluetoothService.MESSAGE_WRITE:
//                byte[] writeBuf = (byte[]) msg.obj;
//                String writeMessage = new String(writeBuf);
                break;
            case BluetoothService.MESSAGE_READ:
//                byte[] readBuf = (byte[]) msg.obj;
//                String readMessage = new String(readBuf, 0, msg.arg1);
//                mConversationArrayAdapter.add("'" + readMessage + "'");
                break;
            case BluetoothService.MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(BluetoothService.DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case BluetoothService.MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(BluetoothService.TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }

    };
    
	private void showState(String msg) {
//		Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
		btStatus.setText(msg);
	}

	private final Handler gyroServerHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case GyroStreamServerClient.MESSAGE_RECEIVED_LINE:
            	handleGyroStreamCommand(msg.getData().getString("message"));
            	break;
            case GyroStreamServerClient.MESSAGE_CONNECT:
                Toast.makeText(GyroStream.this, "connected", Toast.LENGTH_LONG).show();
            	break;
            case GyroStreamServerClient.MESSAGE_DISCONNECT:
                Toast.makeText(GyroStream.this, "disconnected", Toast.LENGTH_LONG).show();
            	break;
            case GyroStreamServerClient.MESSAGE_ERROR:
                Toast.makeText(GyroStream.this, "connection error " + msg.getData().getString("message"), Toast.LENGTH_LONG).show();
            	break;
            }
		}

    };
}