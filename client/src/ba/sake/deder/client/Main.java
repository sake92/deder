package ba.sake.deder.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import ba.sake.deder.client.cli.DederCliClient;
import ba.sake.deder.client.bsp.DederBspProxyClient;

public class Main {

	private static Path logFile;

	public static void main(String[] args) throws Exception {
		var isBspClient = false;
		if (args.length == 1 && args[0].equals("--bsp")) {
			isBspClient = true;
		}

		var processHandle = ProcessHandle.current();
		var parentProcess = processHandle.parent();
		var logFileName = isBspClient ? "bsp-client" : "cli-client";
		logFile = Path.of(".deder/logs/client/" + logFileName + "-" + System.currentTimeMillis() + "-"
				+ processHandle.pid() + ".log");
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

		var serverLockFile = Paths.get(".deder/server.lock");
		if (!serverLockFile.toFile().exists()) {
			System.err.println("Deder server not running, starting it...");
			log("Deder server not running, starting it...");
			ensureJavaInstalled();
			// TODO download server.jar if not present
			startServer();
			System.err.println("Deder server started.");
			log("Deder server started.");
		}

		if (isBspClient) {
			var client = new DederBspProxyClient(logFile);
			client.start();
		} else {
			var client = new DederCliClient(logFile);
			client.start(args);
		}
	}

	private static void ensureJavaInstalled() throws Exception {
		log("Checking if Java is installed...");
		var processBuilder = new ProcessBuilder("java", "-version");
		processBuilder.redirectOutput(logFile.toFile());
		processBuilder.redirectErrorStream(true);
		var process = processBuilder.start();
		var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			log(line);
		}
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			var msg = "Java is not installed or not in PATH. Please install Java to run Deder.";
			System.err.println(msg);
			log(msg);
			System.exit(1);
		}
	}

	private static void startServer() throws Exception {
		var processBuilder = new ProcessBuilder("java", "-jar", ".deder/server.jar");
		processBuilder.redirectOutput(logFile.toFile());
		processBuilder.redirectErrorStream(true);
		var serverProcess = processBuilder.start();
		var exited = serverProcess.waitFor(2, TimeUnit.SECONDS);
		if (exited) {
			System.err
					.println("Failed to start Deder server. Please check log for details: " + logFile.toAbsolutePath());
			System.exit(1);
		}
	}

	private static void log(String message) {
		try {
			Files.writeString(logFile, message + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
