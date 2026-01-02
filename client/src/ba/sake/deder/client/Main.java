package ba.sake.deder.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Properties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import ba.sake.deder.client.cli.DederCliClient;
import ba.sake.deder.client.bsp.DederBspProxyClient;

/*
* Main entry point for Deder clients.
* - never use System.out coz BSP talks to this via stdin/stdout
* - for BSP client we keep trying to reconnect to server indefinitely, 
* because server might be restarting, or shut down due to inactivity
* - for CLI client we try to reconnect for max 10 seconds, then give up
*/
public class Main {

	private static Path logFile;
	private static Path serverLogFile;

	public static void main(String[] args) throws Exception {

		var thisProcess = ProcessHandle.current();

		if (args.length == 2 && args[0].equals("bsp") && args[1].equals("install")) {
			writeBspInstallScript(thisProcess);
			return;
		}

		var isBspClient = false;
		if (args.length == 1 && args[0].equals("bsp")) {
			isBspClient = true;
		}

		var parentProcess = thisProcess.parent();
		var logFileName = isBspClient ? "bsp-client" : "cli-client";
		var timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[^0-9]", "-");
		logFile = Path.of(".deder/logs/client/" + logFileName + "_" + timestamp + "_" + thisProcess.pid() + ".log");
		Files.createDirectories(logFile.getParent());
		Files.createFile(logFile);

		log("Deder client starting...");
		log("Deder client type: " + (isBspClient ? "BSP" : "CLI"));
		log("Arguments: " + String.join(" ", args));
		log("PID: " + thisProcess.pid());
		parentProcess.ifPresentOrElse(pp -> {
			log("Parent PID: " + pp.pid());
			pp.info().commandLine().ifPresent(cmd -> log("Parent Command: " + cmd));
		}, () -> log("No parent process"));

		DederClient client = isBspClient ? new DederBspProxyClient(logFile) : new DederCliClient(args, logFile);
		var startedConnectingAt = Instant.now();
		var maxConnectDurationSeconds = 10;
		var keepConnectingInfinitely = isBspClient;
		var connected = false;
		try {
			client.start();
			connected = true;
		} catch (Exception e) {
			if (args.length == 1 && args[0].equals("shutdown")) {
				log("Deder server not running. No need to shutdown.");
				System.err.println("Deder server not running. No need to shutdown.");
				return;
			}
			startServer(isBspClient);
			while (!connected && (keepConnectingInfinitely
					|| Duration.between(startedConnectingAt, Instant.now()).getSeconds() < maxConnectDurationSeconds)) {
				try {
					var sleepMillis = isBspClient ? 1000 : 100;
					Thread.sleep(sleepMillis);
					log("Attempting to reconnect to server...");
					client.stop();
					client.start();
					connected = true;
				} catch (Exception ex) {
					log("Error occurred while restarting client: " + ex.getMessage());
				}
			}
		}
	}

	private static void startServer(boolean isBspClient) throws Exception {
		System.err.println("Deder server not running, starting it...");
		log("Deder server not running, starting it...");
		ensureJavaInstalled();
		var props = new Properties();
		var propFileName = Paths.get(".deder/server.properties");
		if (Files.exists(propFileName) && Files.isRegularFile(propFileName)) {
			try (FileInputStream inputStream = new FileInputStream(propFileName.toFile())) {
				props.load(inputStream);
			}
		}
		var serverVersion = props.getProperty("version", "early-access");
		var serverLocalPath = props.getProperty("localPath", "");
		if (serverLocalPath != null && !serverLocalPath.isBlank()) {
			// handy for development, use local server build
			Files.copy(Path.of(serverLocalPath), Path.of(".deder/server.jar"), StandardCopyOption.REPLACE_EXISTING);
		} else {
			// TODO figure out server.jar current running version and avoid re-downloading
			// if same version
			download("https://github.com/sake92/deder/releases/download/" + serverVersion + "/deder-server.jar",
					Path.of(".deder/server.jar"));
		}
		startServerProcess(isBspClient, props);
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

	private static void startServerProcess(boolean isBspClient, Properties serverProps) throws Exception {
		var cwd = Paths.get(".").toAbsolutePath();
		var serverLogFile = Path.of(".deder/logs/server.log");
		Files.writeString(serverLogFile, "=".repeat(50) + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		var propFileName = Paths.get(".deder/server.properties");
		var javaOpts = serverProps.getProperty("JAVA_OPTS", "");
		var processArgs = new ArrayList<String>();
		// detach server process so it keeps running after client exits
		if (System.getProperty("os.name").toLowerCase().contains("win")) {
			processArgs.add("cmd");
			processArgs.add("/c");
			processArgs.add("start");
			processArgs.add("/B");
		} else {
			processArgs.add("setsid");
		}
		processArgs.add("java");
		if (!javaOpts.isBlank()) {
			processArgs.addAll(Arrays.asList(javaOpts.split(" ")));
		}
		processArgs.add("-cp");
		processArgs.add(".deder/server.jar");
		processArgs.add("ba.sake.deder.ServerMain");
		processArgs.add("--root-dir");
		processArgs.add(cwd.toString());
		var processBuilder = new ProcessBuilder(processArgs);
		processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(serverLogFile.toFile()));
		processBuilder.redirectErrorStream(true);
		var serverProcess = processBuilder.start();
		var exited = serverProcess.waitFor(2, TimeUnit.SECONDS);
		if (exited) {
			System.err.println("Failed to start Deder server. Please check logs for details: ");
			System.err.println(logFile.toAbsolutePath());
			System.err.println(serverLogFile.toAbsolutePath());
			if (isBspClient) {
				System.err.println("Maybe BSP is disabled? Check .deder/server.properties");
			}
			System.exit(1);
		}
	}

	private static void writeBspInstallScript(ProcessHandle processHandle) throws IOException {
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
		System.err.println("BSP config installed at " + bspConfigPath);
	}

	private static void download(String fileUrl, Path destination) throws Exception {
		System.err.println("Starting download:  " + fileUrl);
		var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
		var request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build();
		var response = client.send(request,
				HttpResponse.BodyHandlers.ofFile(destination, StandardOpenOption.CREATE, StandardOpenOption.WRITE));
		if (response.statusCode() == 200) {
			System.err.println("File downloaded successfully to: " + response.body());
		} else {
			throw new RuntimeException("Failed to download '" + fileUrl + "'. Response: " + response);
		}
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
