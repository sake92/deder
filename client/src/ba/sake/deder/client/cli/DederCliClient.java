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

// TODO write to log file like DederBspProxyClient
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
		if (args.length == 1 && args[0].equals("shutdown")) {
			message = new ClientMessage.Shutdown();
		} else {
			message = new ClientMessage.Run(args);
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
