package ba.sake.deder.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import sun.misc.Signal;
import ba.sake.deder.client.cli.DederCliClient;
import ba.sake.deder.client.bsp.DederBspProxyClient;

/*
 * Main entry point for Deder clients.
 * - never use System.out coz BSP talks to this via stdin/stdout
 * - for BSP client we keep trying to reconect to server indefinitely,
 * because server might be restarting, or shut down due to inactivity
 * - for CLI client we try to reconect for max 10 seconds, then give up
 */
public class Main {

    private static Path logFile;

    DederClient client;

    public static void main(String[] args) throws Exception {
        var main = new Main();
        main.start(args);
    }

    private void start(String[] args) throws Exception {
        Signal.handle(new Signal("INT"), signal -> {
            try {
                if (client != null) {
                    client.stop(true);
                }
            } catch (Exception e) {
                try {
                    log("Error occurred while stopping client: " + e.getMessage());
                } catch (Exception ex) {
                    // ignore, let the process exit
                }
            }
            System.exit(130);
        });

        var thisProcess = ProcessHandle.current();
        var parentProcess = thisProcess.parent();
        var isBspClient = args.length == 1 && args[0].equals("bsp");
        var logFileName = isBspClient ? "bsp-client" : "cli-client";
        var timestamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[^0-9]", "-");
        logFile = Path.of(".deder/logs/client/" + logFileName + "_" + timestamp + "_" + thisProcess.pid() + ".log");
        Files.createDirectories(logFile.getParent());
        Files.createFile(logFile);

        var serverProps = loadServerProperties();

        if (args.length == 2 && args[0].equals("bsp") && args[1].equals("install")) {
            writeBspInstallScript(thisProcess, serverProps);
            return;
        }

        log("Deder client starting...");
        log("Deder client type: " + (isBspClient ? "BSP" : "CLI"));
        log("Arguments: " + String.join(" ", args));
        log("PID: " + thisProcess.pid());
        parentProcess.ifPresentOrElse(pp -> {
            log("Parent PID: " + pp.pid());
            pp.info().commandLine().ifPresent(cmd -> log("Parent Command: " + cmd));
        }, () -> log("No parent process"));

        client = isBspClient ? new DederBspProxyClient(logFile) : new DederCliClient(this::log, args);
        var maxConnectDuration = java.time.Duration.ofSeconds(
                Integer.parseInt(serverProps.getProperty("maxConnectSeconds", "30")));

        try {
            client.start();
        } catch (Exception e) {
            if (args.length == 1 && args[0].equals("shutdown")) {
                log("Deder server not running. No need to shutdown.");
                System.err.println("Deder server not running. No need to shutdown.");
                return;
            }
            startServer(isBspClient, serverProps);
            // start the timer AFTER server process is launched, not before
            var startedConnectingAt = Instant.now();
            var connected = false;
            while (!connected && java.time.Duration.between(startedConnectingAt, Instant.now()).getSeconds() 
                    < maxConnectDuration.getSeconds()) {
                try {
                    var sleepMillis = isBspClient ? 1000 : 100;
                    Thread.sleep(sleepMillis);
                    log("Attempting to reconnect to server...");
                    client.stop(false);
                    client.start();
                    connected = true;
                } catch (Exception ex) {
                    log("Error occurred while restarting client: " + ex.getMessage());
                }
            }
            if (!connected) {
                var msg = "Failed to connect to Deder server after " + maxConnectDuration.getSeconds() 
                        + " seconds. Please check logs for details:";
                log(msg);
                System.err.println(msg);
                System.err.println(logFile.toAbsolutePath());
                System.exit(1);
            }
        }
    }

    private Properties loadServerProperties() {
        var serverProps = new Properties();
        var propFileName = Paths.get(".deder/server.properties");
        if (Files.exists(propFileName) && Files.isRegularFile(propFileName)) {
            try (var inputStream = new FileInputStream(propFileName.toFile())) {
                serverProps.load(inputStream);
            } catch (IOException e) {
                // ignore - use empty properties
            }
        }
        return serverProps;
    }

    private void startServer(boolean isBspClient, Properties serverProps) throws Exception {
        System.err.println("Deder server not running, starting it...");
        log("Deder server not running, starting it...");
        ensureJavaInstalled();

        // Use ArtifactManager for version resolution and artifact handling
        var artifactManager = new ArtifactManager(this::log);
        var serverVersion = artifactManager.resolveServerVersion(serverProps);
        artifactManager.ensureArtifactsAvailable(serverVersion, serverProps);

        startServerProcess(isBspClient, serverProps);
        System.err.println("Deder server started.");
        log("Deder server started.");
    }

    private void ensureJavaInstalled() throws Exception {
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

    private void startServerProcess(boolean isBspClient, Properties serverProps) throws Exception {
        var cwd = Paths.get(".").toAbsolutePath();
        var serverLogFile = Path.of(".deder/logs/server.log");
        Files.writeString(serverLogFile, "=".repeat(50) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        var javaOpts = serverProps.getProperty("JAVA_OPTS", "");
        var processArgs = new ArrayList<String>();
        // detach server process so it keeps running after client exits
        var osname = System.getProperty("os.name").toLowerCase();
        if (osname.contains("win")) {
            processArgs.add("cmd");
            processArgs.add("/c");
            processArgs.add("start");
            processArgs.add("/B");
        } else if (osname.contains("mac")) {
            processArgs.add("nohup");
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
        if (exited && serverProcess.exitValue() != 0) {
            System.err.println("Failed to start Deder server. Please check logs for details: ");
            System.err.println(logFile.toAbsolutePath());
            System.err.println(serverLogFile.toAbsolutePath());
            if (isBspClient) {
                System.err.println("Maybe BSP is disabled? Check .deder/server.properties");
            }
            System.exit(1);
        }
    }

    private void writeBspInstallScript(ProcessHandle processHandle, Properties serverProps) throws IOException {
        System.err.println("Installing BSP config...");
        var clientPath = resolveClientPath(processHandle);
        var commandLineArgsJson = "\"" + clientPath + "\", \"bsp\"";
        Files.createDirectories(Path.of(".bsp"));

        var artifactManager = new ArtifactManager(this::log);
        var serverVersion = artifactManager.resolveServerVersion(serverProps);
        var serverLocalPath = serverProps.getProperty("localPath");
        if (serverLocalPath != null && !serverLocalPath.isBlank()) {
            serverVersion = "local";
        }
        var bspConfig = """
                {
                	"name": "deder-bsp",
                	"argv": [ %s ],
                	"version": "%s",
                	"bspVersion": "2.2.0-M2",
                	"languages": [ "java", "scala" ]
                }
                """.formatted(commandLineArgsJson, serverVersion);
        var bspConfigPath = Path.of(".bsp/deder-bsp.json");
        Files.writeString(bspConfigPath, bspConfig, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        System.err.println("BSP config installed at " + bspConfigPath);
    }

    private String resolveClientPath(ProcessHandle processHandle) {
        // resolve "deder" via PATH (stable across brew/package-manager upgrades)
        try {
            var osname = System.getProperty("os.name").toLowerCase();
            var lookupCmd = osname.contains("win") ? "where" : "which";
            var proc = new ProcessBuilder(lookupCmd, "deder").start();
            var result = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            proc.waitFor();
            if (!result.isBlank()) {
                return result;
            }
        } catch (Exception ignored) {
        }
        return processHandle.info().command().orElseThrow(
                () -> new IllegalStateException("Cannot determine client executable path"));
    }

    private void log(String message) {
        try {
            var logMessage = "[" + LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS) + "] " + message;
            Files.writeString(logFile, logMessage + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}