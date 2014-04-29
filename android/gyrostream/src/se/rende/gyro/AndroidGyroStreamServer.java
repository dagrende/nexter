package se.rende.gyro;

import se.rende.gyro.GyroStreamServerClient.MessageListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Let you communicate with the GyroStream server. Write lines to the server and receive lines from it through a listener.
 * This class delegates communication to a GyroStreamServer.
 * @author dag
 *
 */
public class AndroidGyroStreamServer implements MessageListener {
	private int port = 8123;
	private Handler handler;
//	private GyroStreamServerClient gyroStreamServerClient;
	
	public AndroidGyroStreamServer(String name, Handler handler) {
		this.handler = handler;
//		gyroStreamServerClient = new GyroStreamServerClient(name, host, port);
//		gyroStreamServerClient.addMessageListener(this);
	}
	
	public AndroidGyroStreamServer(String name, Handler handler, int port) {
		this(name, handler);
		this.port = port;
	}
	
	public void connect() {
//		gyroStreamServerClient.connect();
	}
	
	public void disconnect() {
//		gyroStreamServerClient.disconnect();
	}

	
	private void sendHandlerMessage(int messageType, String messageText) {
		Message msg = handler.obtainMessage(messageType);
		if (messageText != null) {
			Bundle bundle = new Bundle();
			bundle.putString("message", messageText);
			msg.setData(bundle);
		}
		msg.sendToTarget();
	}
	
	public void message(int type, String text) {
		sendHandlerMessage(type, text);
	}

	public synchronized void writeLine(String line) {
//		gyroStreamServerClient.writeLine(line);
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}


}
