package world.cryville.relay;

import java.io.IOException;

public class Relay {
	public static void main(String[] args) {
		System.out.println("Relay 0.1.2");
		if (args.length < 1) {
			printUsage();
			return;
		}
		try {
			switch (args[0]) {
				case "s": serverMain(args); break;
				case "r": relayMain(args); break;
				default: printUsage();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	static void serverMain(String[] args) throws IOException {
		if (args.length < 4) {
			printUsage();
			return;
		}
		new ServerClient(
				Integer.parseInt(args[1]),
				args[2],
				Integer.parseInt(args[3])
		);
	}
	static void relayMain(String[] args) throws IOException {
		if (args.length < 3) {
			printUsage();
			return;
		}
		new RelayClient(
				Integer.parseInt(args[1]),
				Integer.parseInt(args[2])
		);
	}
	static void printUsage() {
		System.out.println(
				"Server usage: relay s <serverPort> <relayHost> <relayPort>\n"
				+ "Relay usage: relay r <toServerPort> <toClientPort>"
		);
	}
}
