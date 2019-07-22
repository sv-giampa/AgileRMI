/**
 *  Copyright 2018-2019 Salvatore Giampà
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 **/

package agilermi.codemobility;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Date;

import agilermi.utility.logging.RMILogger;

/**
 * Implements a very basic and simple HTTP server that recognizes the GET method
 * only. This implementation can be enough for many applications that use Java
 * code mobility.
 * 
 * This very simple implementation recognizes only paths and it cannot handle
 * GET queries (such as 'url?param=value").
 * 
 * @author Salvatore Giampa'
 *
 */
public final class BasicCodeServer {

	private RMILogger logger;
	private String codeDirectory = ".";
	private Listener listener;

	/**
	 * Creates a new server instance
	 * 
	 * @return a new {@link BasicCodeServer} instance
	 */
	public static BasicCodeServer create() {
		return new BasicCodeServer();
	}

	/**
	 * Creates the server
	 */
	public BasicCodeServer() {
	}

	public BasicCodeServer setCodeDirectory(String codeDirectory) {
		this.codeDirectory = codeDirectory;
		return this;
	}

	public String getCodeDirectory() {
		return codeDirectory;
	}

	public BasicCodeServer listen(int port) throws IOException {
		return listen(port, true);
	}

	public BasicCodeServer listen(int port, boolean daemon) throws IOException {
		if (listener != null)
			throw new IllegalStateException("Already listening on port " + listener.serverSocket.getLocalPort());
		ServerSocket serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(500);
		listener = new Listener(serverSocket, daemon);
		return this;
	}

	public void stop() {
		if (listener == null)
			return;
		listener.interrupt();
		listener = null;
	}

	public boolean isListening() {
		return listener != null;
	}

	public BasicCodeServer enableDebugLogs() {
		enableDebugLogs(null);
		return this;
	}

	public BasicCodeServer enableDebugLogs(RMILogger logger) {
		this.logger = (logger == null) ? RMILogger.get(BasicCodeServer.class) : logger;
		return this;
	}

	public BasicCodeServer diableDebugLogs() {
		this.logger = null;
		return this;
	}

	private class Listener extends Thread {
		ServerSocket serverSocket;

		public Listener(ServerSocket serverSocket, boolean daemon) {
			this.serverSocket = serverSocket;
			this.setName(this.getClass().getName());
			this.setDaemon(daemon);
			this.start();
		}

		@Override
		public void run() {
			try {
				while (!isInterrupted())
					try {
						new Handler(serverSocket.accept());
					} catch (SocketTimeoutException e) {
					}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class Handler extends Thread {
		Socket socket;

		public Handler(Socket socket) {
			this.socket = socket;
			this.setName(this.getClass().getName());
			this.setDaemon(true);
			this.start();
		}

		@Override
		public void run() {
			try {
				// read request
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				String request = reader.readLine();
				if (request == null)
					return;
				String path = request.split(" ", 4)[1];

				if (logger != null) {
					logger.log("|_HTTP request");
					logger.log("|____request: ", request);
					logger.log("|____client: %s:%d", socket.getInetAddress(), socket.getPort());
				}

				BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
				try {
					File file = Paths.get(codeDirectory, path).toFile();
					if (!file.exists()) {
						throw new InvalidPathException(file.getPath(), "File does not exist");
					}

					// write response header
					output.write("HTTP/1.1 200 OK\n".getBytes());
					output.write(("Date: " + new Date() + "\n").getBytes());
					output.write(("Server: " + BasicCodeServer.class.getName() + "\n").getBytes());
					output.write(("Content-Length: " + file.length() + "\n").getBytes());
					output.write("Content-Type: application/octet-stream\n".getBytes());
					output.write("Connection: Closed\n".getBytes());
					output.write("\n".getBytes());

					// write requested file
					FileInputStream finput = new FileInputStream(file);

					byte[] buffer = new byte[1024];
					int available = 0;
					int bufflen = 0;

					while ((available = finput.available()) > 0) {
						bufflen = finput.read(buffer, 0, Math.min(buffer.length, available));
						if (bufflen == -1)
							break;
						output.write(buffer, 0, bufflen);
					}

					finput.close();
					output.flush();
					output.close();
					socket.close();

				} catch (InvalidPathException e) {
					if (logger != null)
						logger.log("thrown exception: " + e);

					output.write("HTTP/1.1 404 NOT_FOUND\n".getBytes());
					output.write(("Date: " + new Date() + "\n").getBytes());
					output.write(("Server: " + BasicCodeServer.class.getName() + "\n").getBytes());
					output.write("Connection: Closed\n".getBytes());
					output.write("\n".getBytes());
					output.flush();
					output.close();
					socket.close();
					return;
				}
			} catch (IOException e) {
			}
		}
	}

//	public static void main(String[] args) throws IOException {
//		BasicCodeServer.create().listen(80, false);
//	}

}
