package ba.sake.deder.client.bsp;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

// just stream stdin/stdout ...
public class DederBspProxyClient {

	public void start(String[] args) throws IOException {
		var socketPath = Path.of(".deder/server-bsp.sock");
		var processHandle = ProcessHandle.current();
		var logFile = Path.of(".deder/logs/client/bsp-proxy-client-" + System.currentTimeMillis() + "-"
				+ processHandle.pid() + ".log");
		var address = UnixDomainSocketAddress.of(socketPath);
		log(logFile, "Connecting to server...");
		try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			var connected = channel.connect(address);
			log(logFile, "Connected with server!");
			Thread serverWriteThread = new Thread(() -> {
				try {
					var os = Channels.newOutputStream(channel);
					System.in.transferTo(os);
				} catch (IOException e) {
					log(logFile, "Error occurred while writing to server: " + e.getMessage());
					throw new UncheckedIOException(e);
				}
				log(logFile, "Client input stream ended, closing server write stream.");
			}, "DederBspClientWriteThread");
			Thread serverReadThread = new Thread(() -> {
				try {
					var is = Channels.newInputStream(channel);
					is.transferTo(System.out);
				} catch (IOException e) {
					log(logFile, "Error occurred while reading from server: " + e.getMessage());
					throw new UncheckedIOException(e);
				}
				log(logFile, "Server output stream ended, closing client read stream.");
			}, "DederBspClientReadThread");
			serverWriteThread.start();
			serverReadThread.start();
			serverReadThread.join();
			log(logFile, "Server disconnected"); // channel.read == -1
			serverWriteThread.interrupt(); // cancel the write thread
			serverWriteThread.join();

		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void log(Path logFile, String message) {
		try {
			Files.createDirectories(logFile.getParent());
			var openOption = Files.exists(logFile) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE;
			Files.writeString(logFile, message + System.lineSeparator(), StandardCharsets.UTF_8, openOption);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
