package ba.sake.deder.client.bsp;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// just stream stdin/stdout ...
public class DederBspProxyClient {

	private Path logFile;

	public DederBspProxyClient(Path logFile) {
		this.logFile = logFile;
	}

	public void start() throws IOException {
		var socketPath = Path.of(".deder/server-bsp.sock");
		var address = UnixDomainSocketAddress.of(socketPath);
		log("Connecting to server...");
		try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			var connected = channel.connect(address);
			log("Connected with server!");
			Thread serverWriteThread = new Thread(() -> {
				try {
					var os = Channels.newOutputStream(channel);
					System.in.transferTo(os);
				} catch (IOException e) {
					log("Error occurred while writing to server: " + e.getMessage());
					throw new UncheckedIOException(e);
				}
				log("Client input stream ended, closing server write stream.");
			}, "DederBspServerWriteThread");
			Thread serverReadThread = new Thread(() -> {
				try {
					var is = Channels.newInputStream(channel);
					is.transferTo(System.out);
				} catch (IOException e) {
					log("Error occurred while reading from server: " + e.getMessage());
					throw new UncheckedIOException(e);
				}
				log("Server output stream ended, closing client read stream.");
			}, "DederBspServerReadThread");
			serverWriteThread.start();
			serverReadThread.start();
			serverReadThread.join();
			log("Server disconnected"); // channel.read == -1
			serverWriteThread.interrupt(); // cancel the write thread
			serverWriteThread.join();

		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void log(String message) {
		try {
			Files.writeString(logFile, message + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
