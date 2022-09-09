package world.cryville.relay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServerClient {
	int toServerPort;
	ConcurrentHashMap<Integer, SocketHandler> toServerSockets = new ConcurrentHashMap<Integer, SocketHandler>();
	
	Socket toRelaySocket;
	InputStream relayIn;
	DataInputStream relayInData;
	byte[] relayInBuf = new byte[0x10000];
	int relayInBufPos = 0;
	
	boolean inBusy = false;
	SocketHandler inBusySocket;
	int inRemaining = 0;

	OutputStream relayOut;
	DataOutputStream relayOutData;
	Lock relayOutLock = new ReentrantLock();
	
	public ServerClient(int port, String relayHost, int relayPort) throws IOException {
		System.out.println("Server mode");
		toServerPort = port;
		System.out.println("Connecting to relay " + relayHost + ":" + relayPort);
		toRelaySocket = new Socket(relayHost, relayPort);
		System.out.println("Connected to relay successfully");
		relayIn = toRelaySocket.getInputStream();
		relayInData = new DataInputStream(relayIn);
		relayOut = toRelaySocket.getOutputStream();
		relayOutData = new DataOutputStream(relayOut);
		while (true) {
			short op = relayInData.readShort();
			int id = relayInData.readInt();
			int len = relayInData.readInt();
			switch (op) {
			case 0x0000:
				System.out.println("Connection " + id + " establishing");
				SocketHandler handler = new SocketHandler(id, toServerPort);
				toServerSockets.put(id, handler);
				handler.start();
				System.out.println("Connection " + id + " established");
				break;
			case 0x0001:
				try {
					toServerSockets.get(id).close(true);
					toServerSockets.remove(id);
				} catch (NullPointerException ex) {
					System.out.println("Connection " + id + " is closed twice");
				}
				break;
			case 0x0010:
				// System.out.println("Connection " + id + " receiving " + len + " bytes from relay");
				DataInputStreamUtil.readNBytes(relayInData, relayInBuf, 0, len);
				try {
					toServerSockets.get(id).serverOut.write(relayInBuf, 0, len);
				} catch (IOException ex) {
					toServerSockets.get(id).close(false);
				} catch (NullPointerException ex) {
					System.out.println("Connection " + id + " closed; interrupting data");
				}
				break;
			default:
				System.out.println("Unknown op " + op);
			}
		}
	}
	private class SocketHandler extends Thread {
		int id;
		Socket socket;
		public OutputStream serverOut;
		InputStream serverIn;
		byte[] inBuf = new byte[0x10000];
		boolean passiveClose = false;
		public SocketHandler(int id, int port) throws IOException {
			this.id = id;
			try {
				socket = new Socket("localhost", port);
				serverIn = socket.getInputStream();
				serverOut = socket.getOutputStream();
			} catch (IOException ex) {
				activeClose();
			}
		}
		public void close(boolean passive) throws IOException {
			passiveClose = passive;
			System.out.println("Connection " + id + " closing");
			if (!passive) activeClose();
			socket.close();
		}
		void activeClose() {
			System.out.println("Connection " + id + " closing actively");
			relayOutLock.lock();
			try {
				relayOutData.writeShort(0x01);
				relayOutData.writeInt(id);
				relayOutData.writeInt(0);
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				toServerSockets.remove(id);
				relayOutLock.unlock();
			}
		}
		public void run() {
			if (socket.isClosed()) return;
			while (true) {
				int len;
				try {
					len = serverIn.read(inBuf);
				}
				catch (IOException ex) {
					System.out.println("Connection " + id + " closed: " + ex.getMessage());
					if (passiveClose) return;
					if (!socket.isClosed()) activeClose();
					return;
				}
				if (len == -1) return;
				relayOutLock.lock();
				try {
					relayOutData.writeShort(0x10);
					relayOutData.writeInt(id);
					relayOutData.writeInt(len);
					relayOutData.write(inBuf, 0, len);
					// System.out.println("Connection " + id + " writing " + len + " bytes to relay");
				} catch (IOException e1) {
					e1.printStackTrace();
				} finally {
					relayOutLock.unlock();
				}
			}
		}
	}
}
