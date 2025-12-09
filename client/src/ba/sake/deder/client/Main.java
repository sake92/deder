package ba.sake.deder.client;

import java.io.*;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import ba.sake.deder.client.cli.DederCliClient;
import ba.sake.deder.client.bsp.DederBspProxyClient;

public class Main {

	private static Path logFile;
	private static Path serverLogFile;

	public static void main(String[] args) throws Exception {
		var isBspClient = false;
		if (args.length == 1 && args[0].equals("--bsp")) {
			isBspClient = true;
		}

		var processHandle = ProcessHandle.current();
		var parentProcess = processHandle.parent();
		var logFileName = isBspClient ? "bsp-client" : "cli-client";
		var timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[^0-9]", "-");
		logFile = Path.of(".deder/logs/client/" + logFileName + "-" + timestamp + "-" + processHandle.pid() + ".log");
		Files.createDirectories(logFile.getParent());
		Files.createFile(logFile);

		log("Deder client started.");
		log("Client Type: " + (isBspClient ? "BSP" : "CLI"));
		log("Arguments: " + String.join(" ", args));
		log("PID: " + processHandle.pid());
		parentProcess.ifPresentOrElse(pp -> {
			log("Parent PID: " + pp.pid());
			pp.info().commandLine().ifPresent(cmd -> log("Parent Command: " + cmd));
		}, () -> log("No parent process"));

		try {
			startClient(args, isBspClient);
		} catch (Exception e) {
			startServer();
			startClient(args, isBspClient);
		}
	}

	private static void startClient(String[] args, boolean isBspClient) throws Exception {
		if (isBspClient) {
			var client = new DederBspProxyClient(logFile);
			client.start();
		} else {
			var client = new DederCliClient(logFile);
			client.start(args);
		}
	};

	private static void startServer() throws Exception {
		System.err.println("Deder server not running, starting it...");
		log("Deder server not running, starting it...");
		ensureJavaInstalled();
		// TODO download server.jar if not present
		startServerProcess();
		System.err.println("Deder server started.");
		Thread.sleep(1000); // wait a bit for server to start
		log("Deder server started.");
	}

	private static void ensureJavaInstalled() throws Exception {
		log("Checking if Java is installed...");
		var processBuilder = new ProcessBuilder("java", "-version");
		processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
		processBuilder.redirectErrorStream(true);
		var process = processBuilder.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			var msg = "Java is not installed or not in PATH. Please install Java to run Deder.";
			System.err.println(msg);
			log(msg);
			System.exit(1);
		}
		log("Java looks ok.");
	}

	private static void startServerProcess() throws Exception {
		var cwd = Paths.get(".").toAbsolutePath();
		var serverLogFile = Path.of(".deder/logs/server.log");
		Files.writeString(serverLogFile, "=".repeat(50) + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		var processBuilder = new ProcessBuilder("java", "-jar", ".deder/server.jar", "--root-dir", cwd.toString());
		processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(serverLogFile.toFile()));
		processBuilder.redirectErrorStream(true);
		var serverProcess = processBuilder.start();
		var exited = serverProcess.waitFor(2, TimeUnit.SECONDS);
		if (exited) {
			System.err.println("Failed to start Deder server. Please check logs for details: ");
			System.err.println(logFile.toAbsolutePath());
			System.err.println(serverLogFile.toAbsolutePath());
			System.exit(1);
		}
		serverProcess.errorReader().lines().forEach(line -> log("SERVER: " + line));
	}

	private static void log(String message) {
		try {
			var logMessage = "[" + LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + "] " + message;
			Files.writeString(logFile, logMessage + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
