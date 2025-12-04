package ba.sake.deder.client;

import java.io.IOException;
import ba.sake.deder.client.cli.*;
import ba.sake.deder.client.bsp.DederBspProxyClient;

public class Main {

	public static void main(String[] args) throws IOException {
		// TODO start server if not running
		if (args.length == 1 && args[0].equals("--bsp")) {
			var client = new DederBspProxyClient();
			client.start(args);
		} else {
			var logLevel = ServerMessage.LogLevel.INFO;
			if (args.length > 0 && args[0].equals("--log-level")) {
				logLevel = ServerMessage.LogLevel.valueOf(args[1].toUpperCase());
			}
			var client = new DederCliClient(logLevel);
			client.start(args);
		}
	}
}
