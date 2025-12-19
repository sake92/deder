package ba.sake.deder.client.cli;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import ba.sake.deder.client.DederClient;

// TODO handle color stuff https://clig.dev/#output

public class DederCliClient implements DederClient {

	private String[] args;
	private Path logFile;
	private Thread serverWriteThread;
	private Thread serverReadThread;
	private final ObjectMapper jsonMapper = new ObjectMapper();

	public DederCliClient(String[] args, Path logFile) {
		this.args = args;
		this.logFile = logFile;
	}

	@Override
	public void start() throws Exception {
		var socketPath = Path.of(".deder/server-cli.sock");
		var address = UnixDomainSocketAddress.of(socketPath);
		try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
			channel.connect(address);
			var os = Channels.newOutputStream(channel);
			var is = Channels.newInputStream(channel);
			serverWriteThread = new Thread(() -> {
				try {
					serverWrite(os, args);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "DederCliServerWriteThread");
			serverReadThread = new Thread(() -> {
				try {
					serverRead(is);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}, "DederCliServerReadThread");
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

	@Override
	public void stop() throws Exception {
		if (serverWriteThread == null || serverReadThread == null) {
			return; // didn't connect at all
		}
		serverWriteThread.interrupt();
		serverReadThread.interrupt();
		serverWriteThread.join(1000);
		serverReadThread.join(1000);
	}

	private void serverWrite(OutputStream os, String[] args) throws IOException {
		// while (true) {
		// newline delimited JSON messages
		ClientMessage message;
		if (args.length > 0) {
			var leftoverArgs = new String[args.length - 1];
			System.arraycopy(args, 1, leftoverArgs, 0, args.length - 1);
			switch (args[0]) {
			case "version":
				System.out.println("Client version: 0.0.1");
				message = new ClientMessage.Version();
				break;
			case "clean":
				message = new ClientMessage.Clean(leftoverArgs);
				break;
			case "modules":
				message = new ClientMessage.Modules(leftoverArgs);
				break;
			case "tasks":
				message = new ClientMessage.Tasks(leftoverArgs);
				break;
			case "plan":
				message = new ClientMessage.Plan(leftoverArgs);
				break;
			case "shutdown":
				message = new ClientMessage.Shutdown();
				break;
			default:
				message = new ClientMessage.Exec(args);
			}
		} else {
			message = new ClientMessage.Exec(args);
		}
		var messageJson = jsonMapper.writeValueAsString(message);
		log("Sending message to server: " + messageJson);
		os.write((messageJson + '\n').getBytes(StandardCharsets.UTF_8));
		// }
	}

	private void serverRead(InputStream is) throws IOException {
		// newline delimited JSON messages
		var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String messageJson = null;
		while ((messageJson = reader.readLine()) != null) {
			var message = jsonMapper.readValue(messageJson, ServerMessage.class);
			if (message instanceof ServerMessage.Output output) {
				System.out.println(output.text());
			} else if (message instanceof ServerMessage.Log log) {
				System.err.println(log.text());
			} else if (message instanceof ServerMessage.RunSubprocess runSubprocess) {
				// TODO handle multiple messages, restart subprocess
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

	private void log(String message) {
		try {
			Files.writeString(logFile, message + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
