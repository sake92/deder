package ba.sake.deder.client.cli;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

// TODO handle color stuff https://clig.dev/#output

public class DederCliClient {

	private final ObjectMapper jsonMapper = new ObjectMapper();

	public void start(String[] args) throws IOException {
		// TODO pass in debug level

		var socketPath = Path.of(".deder/server-cli.sock");
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
			}, "DederCliServerWriteThread");
			Thread serverReadThread = new Thread(() -> {
				try {
					serverRead(channel);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "DederCliServerReadThread");
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
		System.out.println("Sending message to server: " + messageJson);

		var os = Channels.newOutputStream(channel);
		os.write((messageJson + '\n').getBytes(StandardCharsets.UTF_8));
		os.flush();
		System.out.println("Sent message to server: " + messageJson);
		try {
			Thread.sleep(1000); // wait for server to process
		} catch (InterruptedException e) {
		}
		// }
		System.out.println("serverWrite exiting...");
	}

	void serverRead(SocketChannel channel) throws IOException {
		// newline delimited JSON messages
		var reader = new BufferedReader(
				new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
		var messageJson = "";
		while ((messageJson = reader.readLine()) != null) {
			System.out.println("Received message from server: " + messageJson);
			var message = jsonMapper.readValue(messageJson, ServerMessage.class);
			if (message instanceof ServerMessage.Output output) {
				System.out.println(output.text());
			} else if (message instanceof ServerMessage.Log log) {
				System.err.println(log.text());
			} else if (message instanceof ServerMessage.RunSubprocess runSubprocess) {
				var processBuilder = new ProcessBuilder(runSubprocess.cmd());
				processBuilder.inheritIO();
				var process = processBuilder.start();
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			} else if (message instanceof ServerMessage.Exit exit) {
				System.exit(exit.exitCode()); // TODO cleanup
			}
		}
	}

}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ClientMessage.Run.class, name = "Run") })
sealed interface ClientMessage {

	record Run(String[] args) implements ClientMessage {
	}
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ServerMessage.Output.class, name = "Output"),
		@JsonSubTypes.Type(value = ServerMessage.Log.class, name = "Log"),
		@JsonSubTypes.Type(value = ServerMessage.RunSubprocess.class, name = "RunSubprocess"),
		@JsonSubTypes.Type(value = ServerMessage.Exit.class, name = "Exit") })
sealed interface ServerMessage {

	// goes to stdout
	record Output(String text) implements ServerMessage {
	}

	// goes to stderr
	record Log(String text, Level level) implements ServerMessage {
	}

	record RunSubprocess(String[] cmd) implements ServerMessage {
	}

	record Exit(int exitCode) implements ServerMessage {
	}

	enum Level {
		ERROR, WARNING, INFO, DEBUG, TRACE
	}
}
