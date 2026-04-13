package ba.sake.deder.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import sun.misc.Signal;
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

        var serverProps = new Properties();
        var propFileName = Paths.get(".deder/server.properties");
        if (Files.exists(propFileName) && Files.isRegularFile(propFileName)) {
            try (FileInputStream inputStream = new FileInputStream(propFileName.toFile())) {
                serverProps.load(inputStream);
            }
        }

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
        var maxConnectDurationSeconds = 10;
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
            while (!connected && (isBspClient
                    || Duration.between(startedConnectingAt, Instant.now()).getSeconds() < maxConnectDurationSeconds)) {
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
                var msg = "Failed to connect to Deder server after " + maxConnectDurationSeconds + " seconds. Please check logs for details:";
                log(msg);
                System.err.println(msg);
                System.err.println(logFile.toAbsolutePath());
                System.exit(1);
            }
        }
    }

    private void startServer(boolean isBspClient, Properties serverProps) throws Exception {
        System.err.println("Deder server not running, starting it...");
        log("Deder server not running, starting it...");
        ensureJavaInstalled();
        // must be exactly tag version e.g. v0.1.0
        var serverVersion = resolveServerVersion(serverProps);
        var serverLocalPath = serverProps.getProperty("localPath", "");
        var versionCacheFile = Path.of(".deder/server.current.version");
        Path serverJarPath = Path.of(".deder/server.jar");
        if (serverLocalPath != null && !serverLocalPath.isBlank()) {
            // handy for development, use local server build
            log("Using local server build from " + serverLocalPath);
            Files.copy(Path.of(serverLocalPath), serverJarPath, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(versionCacheFile, "local", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            var cachedVersion = "";
            if (Files.exists(versionCacheFile) && Files.isRegularFile(versionCacheFile)) {
                cachedVersion = Files.readString(versionCacheFile, StandardCharsets.UTF_8).strip();
            }
            if (Files.exists(serverJarPath) && cachedVersion.equals(serverVersion) && !serverVersion.equals("early-access")) {
                log("Server JAR already up-to-date (version " + serverVersion + "), skipping download.");
            } else {
                download("https://github.com/sake92/deder/releases/download/" + serverVersion + "/deder-server.jar",
                        serverJarPath);
                Files.writeString(versionCacheFile, serverVersion, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }
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
        var serverVersion = resolveServerVersion(serverProps);
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
        // fall back to the current process path
        return processHandle.info().command().orElseThrow(
                () -> new IllegalStateException("Cannot determine client executable path"));
    }

    private static final Pattern DEDER_PKL_VERSION_PATTERN = Pattern.compile(
            "amends\\s+\"https://sake92\\.github\\.io/deder/config/([^/]+)/DederProject\\.pkl\"");

    /**
     * Resolves the server version using three sources in priority order:
     * 1. server.properties "version" key
     * 2. deder.pkl amends URL version segment
     * 3. Latest stable release from GitHub (writes a minimal deder.pkl as a side effect)
     */
    private String resolveServerVersion(Properties serverProps) {
        var explicitVersion = serverProps.getProperty("version", "").strip();
        if (!explicitVersion.isBlank()) {
            log("Server version from server.properties: " + explicitVersion);
            return explicitVersion;
        }

        var pklVersion = parseDederPklVersion();
        if (pklVersion.isPresent()) {
            log("Server version from deder.pkl: " + pklVersion.get());
            return pklVersion.get();
        }

        var ghVersion = fetchLatestGithubVersion();
        if (ghVersion.isPresent()) {
            writeMinimalDederPkl(ghVersion.get());
            log("Server version from GitHub releases: " + ghVersion.get());
            return ghVersion.get();
        }

        log("Could not determine server version; falling back to early-access");
        return "early-access";
    }

    private Optional<String> parseDederPklVersion() {
        var pklFile = Path.of("deder.pkl");
        if (!Files.exists(pklFile) || !Files.isRegularFile(pklFile)) {
            return Optional.empty();
        }
        try {
            var content = Files.readString(pklFile, StandardCharsets.UTF_8);
            var matcher = DEDER_PKL_VERSION_PATTERN.matcher(content);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        } catch (IOException e) {
            log("Could not read deder.pkl: " + e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> fetchLatestGithubVersion() {
        try (var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/sake92/deder/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                // Simple extraction of "tag_name" from JSON without pulling in a JSON library
                var body = response.body();
                var tagPattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                var matcher = tagPattern.matcher(body);
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
                log("GitHub releases response did not contain tag_name");
            } else {
                log("GitHub releases API returned HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log("Could not fetch latest version from GitHub: " + e.getMessage());
        }
        return Optional.empty();
    }

    private void writeMinimalDederPkl(String version) {
        var pklFile = Path.of("deder.pkl");
        if (Files.exists(pklFile)) {
            return;
        }
        try {
            var content = "amends \"https://sake92.github.io/deder/config/" + version + "/DederProject.pkl\"\n";
            content += "modules {}\n";
            Files.writeString(pklFile, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.err.println("No deder.pkl found — created minimal config with version " + version);
            log("Created minimal deder.pkl with version " + version);
        } catch (IOException e) {
            log("Could not write minimal deder.pkl: " + e.getMessage());
        }
    }

    private void download(String fileUrl, Path destination) throws Exception {
        System.err.println("Downloading server from " + fileUrl);
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()) {
            var request = HttpRequest.newBuilder().uri(URI.create(fileUrl)).GET().build();
            var response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(destination, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
            if (response.statusCode() == 200) {
                System.err.println("File downloaded successfully to: " + response.body());
            } else {
                throw new RuntimeException("Failed to download '" + fileUrl + "'. Response: " + response);
            }
        }
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
