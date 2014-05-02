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
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	Pattern connectCommandPattern = Pattern.compile("^connect (\\w+)$");
	Pattern disconnectCommandPattern = Pattern.compile("^disconnect (\\w+)$");
	Status status = Status.UNCONNECTED;
	private Socket socket;
	private Writer wr = null;
	private String host = "192.168.0.42";
	private int port = 8123;
	List<MessageListener> listeners = new ArrayList<MessageListener>();
	private final String name;
	public List<String> connectedClients = new ArrayList<String>();
	
	public GyroStreamServerClient(String name, String host, int port) {
		this.name = name;
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
	
	private boolean handleConnectDisconnectCommands(String line) {
		Matcher matcher = connectCommandPattern.matcher(line);
		if (matcher.matches()) {
			String clientName = matcher.group(1);
			connectedClients.add(clientName);
			sendHandlerMessage(MESSAGE_CONNECT, clientName);
			return true;
		}
		matcher = disconnectCommandPattern.matcher(line);
		if (matcher.matches()) {
			String clientName = matcher.group(1);
			connectedClients.remove(clientName);
			sendHandlerMessage(MESSAGE_DISCONNECT, clientName);
			return true;
		}
		return false;
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
				socket = new Socket(host, port);
				status = Status.CONNECTED;
				BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				wr = new OutputStreamWriter(socket.getOutputStream());
				wr.write("connect " + name + "\r");
				wr.flush();
				sendHandlerMessage(MESSAGE_CONNECT);
				while (true) {
					String line = br.readLine();
					if (line == null) {
						break;
					}
					if (!handleConnectDisconnectCommands(line)) {
						sendHandlerMessage(MESSAGE_RECEIVED_LINE, line);
					}
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
		for (MessageListener listener : listeners) {
			listener.message(messageType, messageText);
		}
	}
	
	private void sendHandlerMessage(int messageType) {
		sendHandlerMessage(messageType, null);
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
