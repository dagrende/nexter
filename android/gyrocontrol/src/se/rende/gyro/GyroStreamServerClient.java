package se.rende.gyro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Let you communicate with the GyroStream server. Write lines to the server and receive lines from it through a listener.
 * @author dag
 *
 */
public class GyroStreamServerClient {
//	CONNECT, DISCONNECT, RECEIVED_LINE, ERROR};
	enum Status {UNCONNECTED, CONNECTED}
	public static final int MESSAGE_CONNECT = 1;
	public static final int MESSAGE_DISCONNECT = 2;
	public static final int MESSAGE_RECEIVED_LINE = 3;
	public static final int MESSAGE_ERROR = 4;
	Status status = Status.UNCONNECTED;
	private Socket socket;
	private Writer wr = null;
	private final Handler handler;
	private String host = "192.168.0.42";
	private int port = 8123;
	
	public GyroStreamServerClient(Handler handler) {
		this.handler = handler;
	}
	
	public GyroStreamServerClient(Handler handler, String host, int port) {
		this.handler = handler;
		this.host = host;
		this.port = port;
	}
	
	public void connect() {
		ReaderThread readerThread = new ReaderThread();
		readerThread.start();
	}
	
	public void disconnect() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				sendHandlerMessage(MESSAGE_ERROR, e.toString());
				e.printStackTrace();
			} finally {
				status = Status.UNCONNECTED;
				sendHandlerMessage(MESSAGE_DISCONNECT);
			}
		}
	}

	class ReaderThread extends Thread {

		@Override
		public void run() {
			try {
				socket = new Socket(host, port);
				status = Status.CONNECTED;
				sendHandlerMessage(MESSAGE_CONNECT);
				BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				wr = new OutputStreamWriter(socket.getOutputStream());
				while (true) {
					String line = br.readLine();
					if (line == null) {
						break;
					}
					sendHandlerMessage(MESSAGE_RECEIVED_LINE, line);
				}
			} catch (UnknownHostException e) {
				sendHandlerMessage(MESSAGE_ERROR, e.toString());
				e.printStackTrace();
			} catch (IOException e) {
				sendHandlerMessage(MESSAGE_ERROR, e.toString());
				e.printStackTrace();
			} finally {
				status = Status.UNCONNECTED;
				sendHandlerMessage(MESSAGE_DISCONNECT);
			}
		}

	}
	
	private void sendHandlerMessage(int messageType, String messageText) {
		Message msg = handler.obtainMessage(messageType);
		Bundle bundle = new Bundle();
		bundle.putString("message", messageText);
		msg.setData(bundle);
		msg.sendToTarget();
	}
	
	private void sendHandlerMessage(int messageType) {
		handler.obtainMessage(messageType).sendToTarget();
	}

	public synchronized void writeLine(String line) {
		if (wr != null) {
			try {
				wr.write(line + "\n");
				wr.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

}
