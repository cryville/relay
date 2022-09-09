package world.cryville.relay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RelayClient {
	ServerSocket toServerSocket;
	Socket serverSocket;
	InputStream serverIn;
	DataInputStream serverInData;
	OutputStream serverOut;
	DataOutputStream serverOutData;
	Lock serverOutLock = new ReentrantLock();
	
	byte[] inBuf = new byte[0x10000];
	int inBufPos = 0;
	boolean inBusy = false;
	SocketHandler inBusySocket;
	int inRemaining = 0;
	
	ClientListenerHandler clientListener;
	ServerSocket toClientSocket;
	ConcurrentHashMap<Integer, SocketHandler> toClientSockets = new ConcurrentHashMap<Integer, SocketHandler>();
	
	public RelayClient(int toServerPort, int toClientPort) throws IOException {
		System.out.println("Relay mode");
		toServerSocket = new ServerSocket(toServerPort);
		System.out.println("Listening on port " + toServerPort);
		while (true) {
			serverSocket = toServerSocket.accept();
			System.out.println("Connected to server");
			serverIn = serverSocket.getInputStream();
			serverInData = new DataInputStream(serverIn);
			serverOut = serverSocket.getOutputStream();
			serverOutData = new DataOutputStream(serverOut);
			
			clientListener = new ClientListenerHandler(toClientPort);
			clientListener.start();
			try {
				while (true) {
					short op = serverInData.readShort();
					int id = serverInData.readInt();
					int len = serverInData.readInt();
					switch (op) {
					case 0x0001:
						try {
							toClientSockets.get(id).close(true);
							toClientSockets.remove(id);
						} catch (NullPointerException ex) {
							System.out.println("Connection " + id + " is closed twice");
						}
						break;
					case 0x0010:
						DataInputStreamUtil.readNBytes(serverInData, inBuf, 0, len);
						// System.out.println("Connection " + id + " receiving " + len + " bytes from server");
						try {
							toClientSockets.get(id).clientOut.write(inBuf, 0, len);
						} catch (IOException ex) {
							toClientSockets.get(id).close(false);
						} catch (NullPointerException ex) {
							System.out.println("Connection " + id + " closed; interrupting data");
						}
						break;
					default:
						System.out.println("Unknown op " + op);
					}
				}
			} catch (IOException ex) {
				System.out.println("Server closed");
				clientListener.close();
			}
		}
	}

	private class ClientListenerHandler extends Thread {
		int index = 0;
		public ClientListenerHandler(int port) throws IOException {
			toClientSocket = new ServerSocket(port);
			System.out.println("Listening on port " + port);
		}
		public void close() throws IOException {
			toClientSocket.close();
			for (var s : toClientSockets.values()) {
				s.close(true);
			}
			toClientSockets.clear();
		}
		public void run() {
			while (true) {
				try {
					var socket = toClientSocket.accept();
					var handler = new SocketHandler(index, socket);
					toClientSockets.put(index, handler);
					handler.start();
					index += 1;
				}
				catch (IOException e) {
					System.out.println("Stopped listening");
					return;
				}
			}
		}
	}
	
	private class SocketHandler extends Thread {
		int id;
		Socket socket;
		public OutputStream clientOut;
		InputStream clientIn;
		byte[] inBuf = new byte[0x10000];
		boolean passiveClose = false;
		public SocketHandler(int id, Socket socket) throws IOException {
			this.id = id;
			this.socket = socket;
			clientIn = socket.getInputStream();
			clientOut = socket.getOutputStream();
		}
		public void close(boolean passive) throws IOException {
			passiveClose = passive;
			System.out.println("Connection " + id + " closing");
			if (!passive) activeClose();
			socket.close();
		}
		void activeClose() {
			System.out.println("Connection " + id + " closing actively");
			serverOutLock.lock();
			try {
				serverOutData.writeShort(0x01);
				serverOutData.writeInt(id);
				serverOutData.writeInt(0);
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				toClientSockets.remove(id);
				serverOutLock.unlock();
			}
		}
		public void run() {
			System.out.println("Connection " + id + " establishing");
			try {
				serverOutData.writeShort(0x00);
				serverOutData.writeInt(id);
				serverOutData.writeInt(0);
			} catch (IOException ex) {
				activeClose();
				return;
			}
			System.out.println("Connection " + id + " established");
			while (true) {
				int len;
				try {
					len = clientIn.read(inBuf);
				}
				catch (IOException ex) {
					System.out.println("Connection " + id + " closed: " + ex.getMessage());
					if (passiveClose) return;
					if (!socket.isClosed()) activeClose();
					return;
				}
				if (len == -1) return;
				serverOutLock.lock();
				try {
					serverOutData.writeShort(0x10);
					serverOutData.writeInt(id);
					serverOutData.writeInt(len);
					serverOutData.write(inBuf, 0, len);
					// System.out.println("Connection " + id + " writing " + len + " bytes to server");
				}
				catch (IOException ex) {
					ex.printStackTrace();
				}
				finally {
					serverOutLock.unlock();
				}
			}
		}
	}
}
