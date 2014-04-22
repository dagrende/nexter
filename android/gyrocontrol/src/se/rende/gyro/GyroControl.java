package se.rende.gyro;

import java.util.Timer;
import java.util.TimerTask;

import se.rende.gyro.TwoSticksView.StickChangeListener;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class GyroControl extends Activity implements StickChangeListener {
    private static final int PORT = 8123;
	private static final String HOST = "192.168.0.42";
	private TwoSticksView twoSticksView;
	private TextView statusTextView;
	private GyroStreamServerClient gyroStreamServerClient;
	private Timer sendStickValuesTimer;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		twoSticksView = (TwoSticksView)findViewById(R.id.twoSticks);
		twoSticksView.addStickChangeListener(this);
		statusTextView = (TextView)findViewById(R.id.status);
		statusTextView.setText("Not connected");
		gyroStreamServerClient = new GyroStreamServerClient(handler, HOST, PORT);
		sendStickValuesTimer = new Timer("sendStickValues");
		SeekBar gyroFactor = (SeekBar) findViewById(R.id.gyro_factor);
		gyroFactor.setOnSeekBarChangeListener(onSeekBarChangeListener);
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
        	gyroStreamServerClient.connect();
            return true;
        case R.id.disconnect:
        	gyroStreamServerClient.disconnect();
        	break;
        }
        return false;
    }

    StickValues lastStickValues = new StickValues();
    StickValues stickValues = new StickValues();

	public void changedStick(int stickId, float x, float y) {
		if (stickId == 0) {
			stickValues.up = -y * 100f;
			stickValues.turnCw = x * 100f;
		} else if (stickId == 1) {
			stickValues.forward = -y * 100f;
			stickValues.right = x * 100f;
		}
	}
    
    TimerTask sendStickValuesTimeTask = new TimerTask() {
		@Override
		public void run() {
			if (!lastStickValues.equals(stickValues)) {
//				Log.d("GyroControl", "up=" + stickValues.up + " turnCw=" + stickValues.turnCw + " forward=" + stickValues.forward + " right=" + stickValues.right);
				gyroStreamServerClient.writeLine(
						"s " + (int)stickValues.up +
						" " + (int)stickValues.turnCw +
						" " + (int)stickValues.forward +
						" " + (int)stickValues.right);
				lastStickValues.set(stickValues);
			}
		}
    };
    
	private OnSeekBarChangeListener onSeekBarChangeListener = new OnSeekBarChangeListener() {
		
		public void onStopTrackingTouch(SeekBar seekBar) {
			int progress = seekBar.getProgress();
			float gyroFactor = progress / (float)seekBar.getMax();
			gyroStreamServerClient.writeLine("set gyroFactor " + gyroFactor);
		}
		
		public void onStartTrackingTouch(SeekBar arg0) {
			// TODO Auto-generated method stub
			
		}
		
		public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
			// TODO Auto-generated method stub
			
		}
	};
    private final Handler handler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case GyroStreamServerClient.MESSAGE_RECEIVED_LINE:
            	// from server
            	break;
            case GyroStreamServerClient.MESSAGE_CONNECT:
                Toast.makeText(GyroControl.this, "connected", Toast.LENGTH_LONG).show();
            	statusTextView.setText("Connected to " + HOST + ":" + PORT);
            	sendStickValuesTimer.schedule(sendStickValuesTimeTask, 0l, 100l);
            	break;
            case GyroStreamServerClient.MESSAGE_DISCONNECT:
                Toast.makeText(GyroControl.this, "disconnected", Toast.LENGTH_LONG).show();
            	statusTextView.setText("Not connected");
            	sendStickValuesTimer.cancel();
            	break;
            case GyroStreamServerClient.MESSAGE_ERROR:
                Toast.makeText(GyroControl.this, "connection error " + msg.getData().getString("message"), Toast.LENGTH_LONG).show();
            	break;
            }
		}
    };
    
    class StickValues {
    	float up;
    	float turnCw;
    	float forward;
    	float right;
    	
    	public boolean equals(StickValues v) {
    		return up == v.up && turnCw == v.turnCw && forward == v.forward && right == v.right;
    	}
    	
    	public void set(StickValues v) {
    		up = v.up;
    		turnCw = v.turnCw;
    		forward = v.forward;
    		right = v.right;
    	}
    }
    
}