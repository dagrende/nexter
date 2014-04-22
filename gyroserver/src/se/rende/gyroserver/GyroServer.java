package se.rende.gyroserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server that accepts connections from clients. 
 * Client must start by writing "connect <clientName>". 
 * All other client get "connect <clientName>" for the new client.
 * The new client get "connect <clientName>" for each of the currently connected clients.
 * It listens to data from clients and copies line by line to all other clients.
  * When a client disconnects, the line "disconnect <clientName>" is sent to all.
 * @author dag
 *
 */
public class GyroServer {
	Pattern connectCommandPattern = Pattern.compile("^connect (\\w+)$");
	private NexterServer nexterServer;
	private final int listenerPort;
	List<ClientData> clients = Collections.synchronizedList(new ArrayList<ClientData>());

	public GyroServer(int listenerPort) {
		this.listenerPort = listenerPort;
	}
	
	public void start() {
		nexterServer = new NexterServer();
		nexterServer.start();
	}
	
	private void writeLine(String line, Writer notThisBw) throws IOException {
		synchronized (GyroServer.class) {
			for (ClientData clientData : clients) {
				if (clientData.writer != notThisBw) {
					clientData.writer.write(line + "\n");
					clientData.writer.flush();
				}
			}		
		}
	}
	
	public class NexterServer extends Thread {
		@Override
		public void run() {
			ServerSocket serverSocket = null;
			try {
				serverSocket = new ServerSocket(listenerPort);
				System.out.println("listening to port " + listenerPort);
				while (true) {
					try {
						Socket clientSocket = serverSocket.accept();
						new ReaderThread(clientSocket).start();
					} catch (IOException e) {
						System.err.println("socket accept failure on port " + listenerPort + ": " + e);
						break;
					}
				}
			} catch (IOException e) {
				System.err.println("can't listen to port " + listenerPort + ": " + e);
			}
		}
	}

	public class ReaderThread extends Thread {
		private final InputStream is;
		private BufferedWriter bw;
		private InetAddress remoteIp;
		private ClientData clientData = null;

		public ReaderThread(Socket clientSocket) throws IOException {
			remoteIp = clientSocket.getInetAddress();
			this.is = clientSocket.getInputStream();
			bw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
		}
		
		@Override
		public void run() {
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line = br.readLine();
				Matcher matcher = connectCommandPattern.matcher(line);
				if (matcher.matches()) {
					synchronized (GyroServer.class) {
						// inform this client of other connected
						for (ClientData clientData : clients) {
							bw.write("connect " + clientData.name + "\n");
							bw.flush();
						}
						clientData = new ClientData(matcher.group(1), bw);
						clients.add(clientData);
					}
					// inform other clients of this connect
					String connectMessage = "connect " + clientData.name;
					writeLine(connectMessage, bw);
					System.out.println(connectMessage + " " + remoteIp.getHostAddress());
					
					
					
					while (true) {
						line = br.readLine();
						if (line == null) {
							break;
						}
						writeLine(line, bw);
						System.out.println("received '" + line + "' from " + clientData.name);
					}
				} else {
					bw.write("error: invalid command. Should be connect <clientName>\n");
					bw.flush();
					System.out.println("invalid connect command from " + remoteIp.getHostAddress() + ": " + line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					String disconnectMessage;
					if (clientData != null) {
						disconnectMessage = "disconnect " + clientData.name;
						writeLine(disconnectMessage, bw);
					}
					System.out.println("disconnect " + (clientData != null ? clientData.name : "noname"));
				} catch (IOException e) {
					e.printStackTrace();
				}
				synchronized (GyroServer.class) {
					clients.remove(clientData);
				}
				try {
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public static void main(String[] args) {
		new GyroServer(8123).start();
	}

	static class ClientData {
		String name;
		Writer writer;

		public ClientData(String name, Writer writer) {
			super();
			this.name = name;
			this.writer = writer;
		}
	}
}
