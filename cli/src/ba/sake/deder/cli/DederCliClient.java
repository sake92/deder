package ba.sake.deder.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

// TODO handle color stuff https://clig.dev/#output

public class DederCliClient {

	private final ObjectMapper jsonMapper = new ObjectMapper();

	// TODO in BSP mode it would run forever, listening to server messages ->
	// forwarding stdout
	// and forwarding stdin to server

	// TODO the server needs to know that it's a BSP client communication

	public void start(String[] args) throws IOException {
		var socketPath = Path.of(".deder/server.sock");
		var address = UnixDomainSocketAddress.of(socketPath);
		// System.out.println("Connecting to server...");
		try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(address);
			// System.out.println("Connected with server!");
			Thread serverWriteThread = new Thread(() -> {
				try {
					serverWrite(channel, args);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "write-to-server");
			Thread serverReadThread = new Thread(() -> {
				try {
					serverRead(channel);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "read-from-server");
			serverWriteThread.start();
			serverReadThread.start();
			serverWriteThread.join();
			serverReadThread.join();
			// System.out.println("Server disconnected"); // channel.read == -1
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	void serverWrite(SocketChannel channel, String[] args) throws IOException {
		// while (true) {
		// newline delimited JSON messages
		var message = new ClientMessage.Run(args);
		var messageJson = jsonMapper.writeValueAsString(message);
		var os = Channels.newOutputStream(channel);
		os.write((messageJson + '\n').getBytes(StandardCharsets.UTF_8));
		// }
	}

	void serverRead(SocketChannel channel) throws IOException {
        // newline delimited JSON messages
        var isReader = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
        var messageJson = "";
        while ((messageJson = isReader.readLine()) != null) {
            var message = jsonMapper.readValue(messageJson, ServerMessage.class);
            switch (message) {
                case ServerMessage.Output(var text) -> {
                    System.out.println(text);
                }
                case ServerMessage.Log(var text, var level) -> {
                    System.err.println(text);
                }
                case ServerMessage.RunSubprocess runSubprocess -> {
                    ProcessBuilder processBuilder = new ProcessBuilder(runSubprocess.cmd());
                    processBuilder.inheritIO();
                    Process process = processBuilder.start();
                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                case ServerMessage.Exit(int exitCode) -> {
                    System.exit(exitCode); // TODO cleanup
                }
            }
        }
    }

}
