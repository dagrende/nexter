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
	private int port = 8081;
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
