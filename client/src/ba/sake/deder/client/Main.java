package ba.sake.deder.client;

import java.io.IOException;
import ba.sake.deder.client.cli.DederCliClient;
import ba.sake.deder.client.bsp.DederBspProxyClient;

public class Main {

	public static void main(String[] args) throws IOException {
		if (args.length == 1 && args[0].equals("--bsp")) {
			var client = new DederBspProxyClient();
			client.start(args);
		} else {
			var client = new DederCliClient();
			client.start(args);
		}
	}
}
