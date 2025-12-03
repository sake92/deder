package ba.sake.deder.client.bsp;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

// just stream stdin/stdout ...
public class DederBspProxyClient {

	public void start(String[] args) throws IOException {
		var socketPath = Path.of(".deder/server-bsp.sock");
		var address = UnixDomainSocketAddress.of(socketPath);
		System.out.println("Connecting to server...");
		try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(address);
			System.out.println("Connected with server!");
			Thread serverWriteThread = new Thread(() -> {
				try {
					var os = Channels.newOutputStream(channel);
					System.in.transferTo(os);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "DederBspClientWriteThread");
			Thread serverReadThread = new Thread(() -> {
				try {
					var is = Channels.newInputStream(channel);
					is.transferTo(System.out);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "DederBspClientReadThread");
			serverWriteThread.start();
			serverReadThread.start();
			serverWriteThread.join();
			serverReadThread.join();
			System.out.println("Server disconnected"); // channel.read == -1
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}
