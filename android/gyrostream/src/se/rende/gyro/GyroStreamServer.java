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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Let you communicate with the GyroStream server. Write lines to the server and receive lines from it through a listener.
 * @author dag
 *
 */
public class GyroStreamServer {
	//	CONNECT, DISCONNECT, RECEIVED_LINE, ERROR};
	enum Status {UNCONNECTED, LISTENING, CONNECTED}
	public static final int MESSAGE_CONNECT = 1;
	public static final int MESSAGE_DISCONNECT = 2;
	public static final int MESSAGE_RECEIVED_LINE = 3;
	public static final int MESSAGE_ERROR = 4;
	Pattern connectCommandPattern = Pattern.compile("^connect (\\w+)$");
	Pattern disconnectCommandPattern = Pattern.compile("^disconnect (\\w+)$");
	Status status = Status.UNCONNECTED;
	private ServerSocket listener;
	private Socket socket;
	private Writer wr = null;
	private int port = 8081;
	List<MessageListener> listeners = new ArrayList<MessageListener>();
	private final String name;
	public List<String> connectedClients = new ArrayList<String>();
	
	public GyroStreamServer(String name, int port) {
		this.name = name;
		this.port = port;
	}
	
	public void start() {
		ReaderThread readerThread = new ReaderThread();
		readerThread.start();
	}
	
	public void stop() {
		if (socket != null) {
			try {
				socket.close();
				listener.close();
			} catch (IOException e) {
				sendHandlerMessage(MESSAGE_ERROR, e.toString());
				e.printStackTrace();
			} finally {
				status = Status.UNCONNECTED;
				socket = null;
				sendHandlerMessage(MESSAGE_DISCONNECT);
			}
		}
	}
	
	/**
	 * Returns the names of all currently connected clients to the server (this client is not included).
	 * @return client names
	 */
	public List<String> getConnectedClients() {
		return connectedClients;
	}

	class ReaderThread extends Thread {

		@Override
		public void run() {
			try {
				listener = new ServerSocket(port, 0, null);
				while (true) {
					socket = listener.accept();
					status = Status.CONNECTED;
					BufferedReader br = new BufferedReader(
							new InputStreamReader(socket.getInputStream()));
					wr = new OutputStreamWriter(socket.getOutputStream());
					wr.write("connect " + name + "\r");
					wr.flush();
					sendHandlerMessage(MESSAGE_CONNECT);
					while (true) {
						String line = br.readLine();
						if (line == null) {
							break;
						}
						sendHandlerMessage(MESSAGE_RECEIVED_LINE, line);
					}
					status = Status.UNCONNECTED;
					sendHandlerMessage(MESSAGE_DISCONNECT);
				}
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
		for (MessageListener listener : listeners) {
			listener.message(messageType, messageText);
		}
	}
	
	private void sendHandlerMessage(int messageType) {
		sendHandlerMessage(messageType, null);
	}

	/**
	 * Sends a line to the connected client, if any.
	 * @param line
	 */
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

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public interface MessageListener {
		void message(int type, String text);
	}
	
	public void addMessageListener(MessageListener listener) {
		listeners.add(listener);
	}

	public void removeMessageListener(MessageListener listener) {
		listeners.remove(listener);
	}

}
