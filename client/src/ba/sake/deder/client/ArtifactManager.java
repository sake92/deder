package ba.sake.deder.client;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Manages server artifact resolution and downloading.
 * 
 * Responsibilities:
 * - Resolve server version from multiple sources (server.properties → deder.pkl → GitHub API)
 * - Ensure server.jar and test-runner.jar are available (local, cache, or direct download)
 * - Manage version cache file (.deder/server.current.version)
 * - DRY artifact download logic for both server and test-runner
 */
public class ArtifactManager {

    // Constants
    private static final String GITHUB_REPO = "sake92/deder";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String GITHUB_DOWNLOAD_URL = "https://github.com/" + GITHUB_REPO + "/releases/download";

    // Paths
    private static final Path VERSION_CACHE_FILE = Path.of(".deder/server.current.version");
    private static final Path SERVER_JAR_PATH = Path.of(".deder/server.jar");
    private static final Path TEST_RUNNER_JAR_PATH = Path.of(".deder/test-runner.jar");

    // Pattern for parsing deder.pkl version
    private static final Pattern DEDER_PKL_VERSION_PATTERN = Pattern.compile(
            "amends\\s+\"https://sake92\\.github\\.io/deder/config/([^/]+)/DederProject\\.pkl\"");

    // Dependencies
    private ArtifactCache cache;
    private final Consumer<String> logger;

    /**
     * Creates a new ArtifactManager
     * 
     * @param logger logging consumer
     */
    public ArtifactManager(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * Resolves the server version using three sources in priority order:
     * 1. server.properties "version" key
     * 2. deder.pkl amends URL version segment
     * 3. Latest stable release from GitHub (writes a minimal deder.pkl as a side effect)
     * 
     * @param serverProps server properties
     * @return resolved version string (e.g., "v0.5.1" or "early-access")
     */
    public String resolveServerVersion(Properties serverProps) {
        // 1. Check explicit version in server.properties
        var explicitVersion = serverProps.getProperty("version", "").strip();
        if (!explicitVersion.isBlank()) {
            log("Server version from server.properties: " + explicitVersion);
            return explicitVersion;
        }

        // 2. Parse from deder.pkl
        var pklVersion = parseDederPklVersion();
        if (pklVersion.isPresent()) {
            log("Server version from deder.pkl: " + pklVersion.get());
            return pklVersion.get();
        }

        // 3. Fetch from GitHub
        var ghVersion = fetchLatestGithubVersion();
        if (ghVersion.isPresent()) {
            writeMinimalDederPkl(ghVersion.get());
            log("Server version from GitHub releases: " + ghVersion.get());
            return ghVersion.get();
        }

        log("Could not determine server version; falling back to early-access");
        return "early-access";
    }

    /**
     * Ensures server.jar and test-runner.jar are available.
     * Uses local path, cache, or direct download as appropriate.
     * 
     * @param version the resolved version
     * @param serverProps server properties containing localPath/testRunnerLocalPath
     */
    public void ensureArtifactsAvailable(String version, Properties serverProps) throws Exception {
        String cachedVersion = readCachedVersion();

        // Initialize cache if appropriate
        String serverLocalPath = serverProps.getProperty("localPath", "");
        String testRunnerLocalPath = serverProps.getProperty("testRunnerLocalPath", "");
        
        boolean useCache = (serverLocalPath == null || serverLocalPath.isBlank())
                && !version.equals("early-access");
        
        if (useCache) {
            try {
                cache = new ArtifactCache(logger);
            } catch (Exception e) {
                log("Failed to initialize artifact cache: " + e.getMessage() + ", falling back to direct download");
                useCache = false;
                cache = null;
            }
        }

        // Ensure server.jar
        ensureArtifact(
            ArtifactCache.ArtifactType.SERVER,
            version,
            serverLocalPath,
            SERVER_JAR_PATH,
            cachedVersion
        );

        // Ensure test-runner.jar
        ensureArtifact(
            ArtifactCache.ArtifactType.TEST_RUNNER,
            version,
            testRunnerLocalPath,
            TEST_RUNNER_JAR_PATH,
            cachedVersion
        );

        // Update version cache (only on successful server.jar handling)
        if (serverLocalPath == null || serverLocalPath.isBlank()) {
            String versionToCache = version.equals("early-access") ? "local" : version;
            writeCachedVersion(versionToCache);
        }
    }

    /**
     * Ensures a single artifact is available.
     * Uses local path, cache, or direct download.
     */
    private void ensureArtifact(
        ArtifactCache.ArtifactType type,
        String version,
        String localPath,
        Path targetPath,
        String cachedVersion
    ) throws Exception {
        String artifactName = type == ArtifactCache.ArtifactType.SERVER ? "server" : "test-runner";

        // Case 1: Local path specified - copy directly (bypass cache)
        if (localPath != null && !localPath.isBlank()) {
            log("Using local " + artifactName + " build from " + localPath);
            System.err.println("Using local " + artifactName + "...");
            Files.copy(Path.of(localPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        // Case 2: Already up-to-date - skip download
        if (Files.exists(targetPath) && cachedVersion.equals(version) && !version.equals("early-access")) {
            log(artifactName + " JAR already up-to-date (version " + version + "), skipping download.");
            return;
        }

        // Case 3: early-access - direct download (no cache)
        if (version.equals("early-access")) {
            log("Downloading " + artifactName + " from GitHub (early-access)...");
            downloadDirect(type, version, targetPath);
            return;
        }

        // Case 4: Stable version - try cache, fallback to direct download
        if (cache != null) {
            try {
                System.err.println("Copying " + artifactName + " from global cache (version " + version + ")...");
                Path cachedArtifact = cache.getArtifact(version, type);
                Files.copy(cachedArtifact, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log("Copied " + artifactName + " JAR from global cache to .deder/");
                return;
            } catch (Exception e) {
                log("Cache operation failed: " + e.getMessage() + ", falling back to direct download");
                downloadDirect(type, version, targetPath);
                return;
            }
        }

        // Fallback: direct download
        downloadDirect(type, version, targetPath);
    }

    /**
     * Downloads artifact directly from GitHub (fallback when cache unavailable)
     */
    private void downloadDirect(ArtifactCache.ArtifactType type, String version, Path targetPath) throws Exception {
        String fileName = ArtifactCache.getArtifactFileName(type);
        String url = GITHUB_DOWNLOAD_URL + "/" + version + "/" + fileName;
        
        log("Downloading " + fileName + " from: " + url);
        System.err.println("Downloading " + fileName + " from GitHub...");
        
        try (var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()) {
            
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            var response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(targetPath,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING));
            
            if (response.statusCode() == 200) {
                log("Downloaded to: " + response.body());
            } else {
                throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
            }
        }
    }

    /**
     * Reads cached version from file
     */
    private String readCachedVersion() {
        try {
            if (Files.exists(VERSION_CACHE_FILE) && Files.isRegularFile(VERSION_CACHE_FILE)) {
                return Files.readString(VERSION_CACHE_FILE, StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            log("Could not read cached version: " + e.getMessage());
        }
        return "";
    }

    /**
     * Writes version to cache file
     */
    private void writeCachedVersion(String version) {
        try {
            Files.writeString(VERSION_CACHE_FILE, version, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log("Could not write cached version: " + e.getMessage());
        }
    }

    /**
     * Parses version from deder.pkl
     * Finds the last non-commented "amends" line
     */
    private Optional<String> parseDederPklVersion() {
        var pklFile = Path.of("deder.pkl");
        if (!Files.exists(pklFile) || !Files.isRegularFile(pklFile)) {
            return Optional.empty();
        }
        try {
            var content = Files.readString(pklFile, StandardCharsets.UTF_8);
            var lines = content.split("\\r?\\n");
            
            // Process lines in reverse to find the LAST active amends
            for (int i = lines.length - 1; i >= 0; i--) {
                var line = lines[i].strip();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                var matcher = DEDER_PKL_VERSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }
        } catch (IOException e) {
            log("Could not read deder.pkl: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Fetches latest version from GitHub API
     */
    private Optional<String> fetchLatestGithubVersion() {
        try (var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            
            if (response.statusCode() == 200) {
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

    /**
     * Writes minimal deder.pkl if not exists
     */
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

    private void log(String message) {
        logger.accept(message);
    }
}