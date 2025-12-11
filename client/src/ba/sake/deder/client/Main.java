package ba.sake.deder.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import ba.sake.deder.client.cli.DederCliClient;
import ba.sake.deder.client.bsp.DederBspProxyClient;

public class Main {

	private static Path logFile;
	private static Path serverLogFile;

	public static void main(String[] args) throws Exception {

		var processHandle = ProcessHandle.current();

		if (args.length == 2 && args[0].equals("bsp") && args[1].equals("install")) {
			System.err.println("Installing BSP config...");
			var commandLineArgs = new ArrayList<String>();
			commandLineArgs.add(processHandle.info().command().get());
			commandLineArgs.addAll(Arrays.asList(processHandle.info().arguments().get()));
			// this is called with "bsp install", need to remove "install"
			commandLineArgs.remove(commandLineArgs.size() - 1);
			var commandLineArgsJson = commandLineArgs.stream().map(arg -> "\"" + arg + "\"")
					.collect(Collectors.joining(", "));
			Files.createDirectories(Path.of(".bsp"));
			var bspConfig = """
					{
						"name": "deder-bsp",
						"argv": [ %s ],
						"version": "0.0.1",
						"bspVersion": "2.2.0-M2",
						"languages": [ "java", "scala" ]
					}
					""".formatted(commandLineArgsJson);
			var bspConfigPath = Path.of(".bsp/deder-bsp.json");
			Files.write(bspConfigPath, bspConfig.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
			System.err.println("BSP config installed at " + bspConfigPath.toAbsolutePath());
			return;
		}

		var isBspClient = false;
		if (args.length == 1 && args[0].equals("bsp")) {
			isBspClient = true;
		}

		// TODO handle "shutdown" command to stop server, if not running just exit

		var parentProcess = processHandle.parent();
		var logFileName = isBspClient ? "bsp-client" : "cli-client";
		var timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[^0-9]", "-");
		logFile = Path.of(".deder/logs/client/" + logFileName + "_" + timestamp + "_" + processHandle.pid() + ".log");
		Files.createDirectories(logFile.getParent());
		Files.createFile(logFile);

		log("Deder client starting...");
		log("Deder client type: " + (isBspClient ? "BSP" : "CLI"));
		log("Arguments: " + String.join(" ", args));
		log("PID: " + processHandle.pid());
		parentProcess.ifPresentOrElse(pp -> {
			log("Parent PID: " + pp.pid());
			pp.info().commandLine().ifPresent(cmd -> log("Parent Command: " + cmd));
		}, () -> log("No parent process"));

		DederClient client = isBspClient ? new DederBspProxyClient(logFile) : new DederCliClient(args, logFile);

		var currentAttempt = 1;
		var connected = false;
		try {
			client.start();
			connected = true;
		} catch (Exception e) {
			startServer();
			while (!connected && currentAttempt <= 10) {
				try {
					Thread.sleep(1000);
					System.err.println("Attempting to reconnect to server, attempt " + currentAttempt + "...");
					log("Attempting to reconnect to server, attempt " + currentAttempt + "...");
					currentAttempt++;
					client.stop();
					client.start();
					connected = true;
				} catch (Exception ex) {
					log("Error occurred while restarting client: " + ex.getMessage());
				}
			}
		}
	}

	private static void startServer() throws Exception {
		System.err.println("Deder server not running, starting it...");
		log("Deder server not running, starting it...");
		ensureJavaInstalled();
		// TODO download server.jar if not present
		Files.copy(Path.of("/home/sake/projects/sake92/deder/out/server/assembly.dest/out.jar"),
				Path.of(".deder/server.jar"), StandardCopyOption.REPLACE_EXISTING);
		startServerProcess();
		System.err.println("Deder server started.");
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
