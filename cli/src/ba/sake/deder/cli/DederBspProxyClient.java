package ba.sake.deder.cli;

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
					serverWrite(channel, args);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "DederBspClientWriteThread");
			Thread serverReadThread = new Thread(() -> {
				try {
					serverRead(channel);
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

	void serverWrite(SocketChannel channel, String[] args) throws IOException {
		var os = Channels.newOutputStream(channel);
		System.in.transferTo(os);
	}

	void serverRead(SocketChannel channel) throws IOException {
        var isReader = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
        
    }

}
