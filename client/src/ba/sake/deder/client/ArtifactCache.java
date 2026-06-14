package ba.sake.deder.client;

import io.avaje.jsonb.Jsonb;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages global artifact cache at ~/.deder/cache/artifacts/ Stores server.jar
 * and test-runner.jar from GitHub releases.
 * 
 * Features: - Downloads and caches all stable versions - Verifies checksums for
 * integrity - Handles concurrent access safely - Graceful fallback on failures
 */
public class ArtifactCache {

	private static final String GITHUB_REPO = "sake92/deder";
	private static final String CACHE_BASE_DIR = ".deder" + File.separator + "cache" + File.separator + "artifacts";
	private static final String GITHUB_API_URL = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/";
	private static final String GITHUB_DOWNLOAD_URL = "https://github.com/" + GITHUB_REPO + "/releases/download";
	private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);
	private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LOCK_POLL_INTERVAL = Duration.ofMillis(500);
	private static final String LOCK_FILE_NAME = ".downloading";

	// File name mappings
	private static final Map<ArtifactType, String> ARTIFACT_FILE_NAMES = Map.of(ArtifactType.SERVER, "deder-server.jar",
			ArtifactType.TEST_RUNNER, "deder-test-runner.jar");

	/**
	 * Gets the artifact file name for a given type
	 */
	public static String getArtifactFileName(ArtifactType type) {
		return ARTIFACT_FILE_NAMES.get(type);
	}

	// Client singleton
	private static final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
			.followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();

	// For atomic check-then-act operations
	private final Path cacheBasePath;
	
	// Release info file for GitHub API freshness check
	private static final String RELEASE_INFO_FILE = ".release-info";
	// Minimum interval between GitHub API freshness checks (avoids rate limits)
	private static final Duration API_CHECK_COOLDOWN = Duration.ofMinutes(5);

	private final java.util.function.Consumer<String> logger;

	public enum ArtifactType {
		SERVER("server"), TEST_RUNNER("test-runner");

		private final String name;

		ArtifactType(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public ArtifactCache() {
		this((String msg) -> System.err.println(msg));
	}

	public ArtifactCache(java.util.function.Consumer<String> logger) {
		this.logger = msg -> {
			logger.accept(msg);
			// Also log important messages to stderr
			if (msg.contains("ownloading") || msg.contains("lock") || msg.contains("error")) {
				System.err.println(msg);
			}
		};
		String userHome = System.getProperty("user.home");
		this.cacheBasePath = Paths.get(userHome, CACHE_BASE_DIR);
		log("Initializing cache at: " + cacheBasePath);
	}

	/**
	 * Gets the artifact for the given version and type. Downloads and caches if not
	 * already cached.
	 * 
	 * @param version the version tag (e.g., "v0.5.1")
	 * @param type    the artifact type (SERVER or TEST_RUNNER)
	 * @return path to the cached artifact
	 * @throws Exception if download/caching fails
	 */
	public Path getArtifact(String version, ArtifactType type) throws Exception {
		validateVersion(version, type);

		log("Getting artifact: " + type.getName() + " version " + version);

		// Check cache first
		Path cachedPath = getCachedArtifactPath(version, type);
		if (Files.exists(cachedPath)) {
			log("Found cached artifact at: " + cachedPath);

			// Verify checksum
			if (verifyCachedChecksum(version, type)) {
				// For early-access, check freshness via GitHub API
				if ("early-access".equals(version)) {
					if (!isEarlyAccessReleaseStale(version)) {
						System.err.println("Using cached early-access " + type.getName() + ".jar");
						return cachedPath;
					}
					System.err.println("Early-access " + type.getName() + " updated, downloading...");
					// Remove the stale-but-intact artifact so downloadAndCache doesn't
					// short-circuit on its local checksum guard and return the old jar.
					Files.deleteIfExists(cachedPath);
					Files.deleteIfExists(getChecksumPath(version, type));
				} else {
					log("Cache hit, checksum verified for " + version);
					return cachedPath;
				}
			} else {
				log("Cached artifact checksum mismatch, re-downloading");
				// Remove corrupted artifact
				Files.deleteIfExists(cachedPath);
				Files.deleteIfExists(getChecksumPath(version, type));
			}
		}

		// Download and cache
		Path result = downloadAndCache(version, type);

		// After successful early-access download, store release metadata for freshness
		if ("early-access".equals(version)) {
			try {
				String remoteUpdatedAt = fetchReleaseUpdatedAt(version);
				if (remoteUpdatedAt != null) {
					writeReleaseTimestamp(version, "updated_at", remoteUpdatedAt);
				}
			} catch (Exception e) {
				log("Could not update release metadata: " + e.getMessage());
			}
		}

		return result;
	}

	/**
	 * Downloads an artifact and stores it in the cache
	 */
	private Path downloadAndCache(String version, ArtifactType type) throws Exception {
		Path versionDir = getVersionDir(version);

		String artifactFileName = ARTIFACT_FILE_NAMES.get(type);
		Path artifactPath = versionDir.resolve(artifactFileName);
		Path checksumPath = versionDir.resolve(artifactFileName + ".sha256");

		// Try to acquire lock - returns false if another process is downloading
		boolean shouldDownload = acquireLock(version);

		// Check again if another process finished downloading while we waited
		if (Files.exists(artifactPath) && Files.exists(checksumPath)) {
			if (verifyCachedChecksum(version, type)) {
				log("Artifact already downloaded by another process");
				releaseLock(version);
				return artifactPath;
			}
		}

		if (!shouldDownload) {
			// Another process is downloading, wait for them to finish
			log("Waiting for other process to finish download...");
			Instant startTime = Instant.now();
			while (Duration.between(startTime, Instant.now()).compareTo(LOCK_WAIT_TIMEOUT) < 0) {
				Thread.sleep(LOCK_POLL_INTERVAL.toMillis());
				if (Files.exists(artifactPath) && Files.exists(checksumPath)) {
					if (verifyCachedChecksum(version, type)) {
						log("Other process finished downloading");
						releaseLock(version);
						return artifactPath;
					}
				}
			}
			// Timeout waiting, try to download ourselves
			log("Timeout waiting, will try to download");
		}

		try {
			// Download to temp file first (safe download pattern)
			Path tempDownload = versionDir.resolve(artifactFileName + ".tmp");

			// Get checksums from GitHub
			Map<String, String> checksums = fetchChecksums(version);

			String expectedHash = checksums.get(artifactFileName);
			if (expectedHash == null) {
				throw new RuntimeException("No checksum found for " + artifactFileName + " in release " + version);
			}

			// Download artifact to temp file
			String downloadUrl = GITHUB_DOWNLOAD_URL + "/" + version + "/" + artifactFileName;
			log("Downloading from: " + downloadUrl);

			downloadFile(downloadUrl, tempDownload);
			log("Downloaded artifact to temp: " + tempDownload);

			// Verify checksum on temp file
			String actualHash = computeSHA256(tempDownload);

			if (!expectedHash.equals(actualHash)) {
				log("ERROR: Checksum mismatch! Expected: " + expectedHash + ", Actual: " + actualHash);
				Files.deleteIfExists(tempDownload);
				throw new RuntimeException("Checksum verification failed for " + artifactFileName);
			}

			log("Checksum verified successfully on temp file");

			// Atomic move to final location
			Files.move(tempDownload, artifactPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			log("Moved artifact to final location: " + artifactPath);

			// Store checksum for future verification
			Files.writeString(checksumPath, expectedHash, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);

		} catch (Exception e) {
			// Clean up on failure will be handled automatically by checking for .sha256
			// file
			releaseLock(version);
			throw e;
		} finally {
			// Always release lock
			releaseLock(version);
		}

		return artifactPath;
	}

	/**
	 * Fetches checksums from GitHub releases
	 */
	private Map<String, String> fetchChecksums(String version) throws Exception {
		String checksumsUrl = GITHUB_DOWNLOAD_URL + "/" + version + "/checksums_sha256.txt";
		log("Fetching checksums from: " + checksumsUrl);

		try {
			Path tempFile = Files.createTempFile("checksums", ".txt");

			try {
				downloadFile(checksumsUrl, tempFile);
				String content = Files.readString(tempFile, StandardCharsets.UTF_8);

				return parseChecksums(content);
			} finally {
				Files.deleteIfExists(tempFile);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to fetch checksums: " + e.getMessage(), e);
		}
	}

	/**
	 * Parses checksums from content Format: "<sha256> " or ": <sha256>"
	 */
	private Map<String, String> parseChecksums(String content) {
		Map<String, String> result = new HashMap<>();

		// Pattern 1: "<sha256> " (standard format)
		Pattern p1 = Pattern.compile("^([a-f0-9]+)\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
		// Pattern 2: ": <sha256>" (alternative)
		Pattern p2 = Pattern.compile("^(\\S+):\\s*([a-f0-9]+)$", Pattern.CASE_INSENSITIVE);

		String[] lines = content.split("\\r?\\n");
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			Matcher m1 = p1.matcher(line);
			if (m1.find()) {
				result.put(m1.group(2), m1.group(1));
				continue;
			}

			Matcher m2 = p2.matcher(line);
			if (m2.find()) {
				result.put(m2.group(1), m2.group(2));
			}
		}

		log("Parsed " + result.size() + " checksums");
		return result;
	}

	/**
	 * Computes SHA-256 hash of a file
	 */
	private String computeSHA256(Path filePath) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");

		try (InputStream is = Files.newInputStream(filePath)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = is.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		}

		byte[] hash = digest.digest();
		StringBuilder sb = new StringBuilder();
		for (byte b : hash) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	/**
	 * Verifies local integrity: compares the cached JAR's SHA-256 against the
	 * locally stored .sha256 file. This catches corruption, not staleness.
	 */
	private boolean verifyCachedChecksum(String version, ArtifactType type) {
		Path checksumPath = getChecksumPath(version, type);

		if (!Files.exists(checksumPath)) {
			log("No checksum file found, treating as invalid");
			return false;
		}

		try {
			String expectedHash = Files.readString(checksumPath, StandardCharsets.UTF_8).strip();
			Path artifactPath = getCachedArtifactPath(version, type);

			if (!Files.exists(artifactPath)) {
				return false;
			}

			String actualHash = computeSHA256(artifactPath);
			return expectedHash.equals(actualHash);
		} catch (Exception e) {
			log("Checksum verification error: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Downloads a file from URL to destination
	 */
	private void downloadFile(String url, Path destination) throws Exception {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(DOWNLOAD_TIMEOUT).build();

		HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination,
				StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));

		if (response.statusCode() != 200) {
			throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
		}
	}

	/**
	 * Acquires a download lock for a specific version. Uses a marker file to
	 * prevent concurrent downloads of the same version. Returns true if lock was
	 * acquired, false if another process is downloading.
	 * 
	 * @param version the version to lock
	 * @return true if this process should download, false if another is downloading
	 */
	private boolean acquireLock(String version) throws InterruptedException {
		Path versionDir = getVersionDir(version);
		Path lockFile = versionDir.resolve(LOCK_FILE_NAME);
		Path lockInfoFile = versionDir.resolve(LOCK_FILE_NAME + ".info");

		// Ensure version directory exists
		try {
			Files.createDirectories(versionDir);
		} catch (Exception e) {
			// Directory might already exist, that's fine
		}

		Instant startTime = Instant.now();

		// Try to create lock file atomically
		while (Duration.between(startTime, Instant.now()).compareTo(LOCK_WAIT_TIMEOUT) < 0) {
			try {
				// Try to create the lock file exclusively
				Files.createFile(lockFile);

				// Write info about this process
				String lockInfo = "PID=" + ProcessHandle.current().pid() + "\n" + "Started=" + Instant.now() + "\n"
						+ "Host=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n"
						+ "User=" + System.getProperty("user.name");
				Files.writeString(lockInfoFile, lockInfo, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);

				log("Acquired download lock for version " + version);
				return true;
			} catch (FileAlreadyExistsException e) {
				// Another process is downloading, check if they're still alive
				boolean processDead = false;
				try {
					String info = Files.readString(lockInfoFile, StandardCharsets.UTF_8);
					// Extract PID from info like "PID=12345"
					String pidStr = info.lines().filter(l -> l.startsWith("PID=")).findFirst().orElse("").substring(4)
							.strip();
					if (!pidStr.isEmpty()) {
						long pid = Long.parseLong(pidStr);
						// Check if process is still running
						processDead = ProcessHandle.of(pid).filter(p -> p.isAlive()).isEmpty();
					}
					if (processDead) {
						log("Stale lock detected from dead process " + pidStr + ", cleaning up");
						// Try to clean up the stale lock
						Files.deleteIfExists(lockFile);
						Files.deleteIfExists(lockInfoFile);
						// Brief pause before retry
						Thread.sleep(100);
						continue;
					}
				} catch (Exception ignored) {
				}

				if (processDead) {
					continue; // Will retry acquiring lock
				}

				log("Another process is downloading version " + version + ", waiting...");

				// Wait a bit and try again
				Thread.sleep(LOCK_POLL_INTERVAL.toMillis());
			} catch (Exception e) {
				log("Error acquiring lock: " + e.getMessage());
				// Could be permission issue or other error, proceed anyway
				return true;
			}
		}

		log("Timeout waiting for lock, proceeding anyway");
		return true; // Proceed if timeout
	}

	/**
	 * Releases the download lock for a version
	 */
	private void releaseLock(String version) {
		try {
			Path versionDir = getVersionDir(version);
			Path lockFile = versionDir.resolve(LOCK_FILE_NAME);
			Path lockInfoFile = versionDir.resolve(LOCK_FILE_NAME + ".info");

			Files.deleteIfExists(lockFile);
			Files.deleteIfExists(lockInfoFile);

			log("Released download lock for version " + version);
		} catch (Exception e) {
			log("Error releasing lock: " + e.getMessage());
		}
	}

	/**
	 * Checks freshness of the early-access release via the GitHub API.
	 * Compares the release's updated_at timestamp against a locally stored value.
	 * Respects a cooldown period to avoid hitting GitHub API rate limits.
	 *
	 * @return true if the cached artifact is stale (should re-download), false if fresh
	 */
	private boolean isEarlyAccessReleaseStale(String version) {
		try {
			// Check cooldown: skip API call if recently checked
			String lastCheck = readReleaseTimestamp(version, "last_check");
			if (lastCheck != null) {
				Instant lastCheckTime = Instant.parse(lastCheck);
				if (Duration.between(lastCheckTime, Instant.now()).compareTo(API_CHECK_COOLDOWN) < 0) {
					log("Skipping API freshness check (cooldown active, last checked: " + lastCheck + ")");
					String storedUpdatedAt = readReleaseTimestamp(version, "updated_at");
					// If we have no stored timestamp, treat as stale to be safe
					return storedUpdatedAt == null;
				}
			}

			// Fetch current release metadata from GitHub API
			String remoteUpdatedAt = fetchReleaseUpdatedAt(version);
			// Record that we checked
			writeReleaseTimestamp(version, "last_check", Instant.now().toString());

			if (remoteUpdatedAt == null) {
				// API call failed — treat as stale to trigger re-download (safe fallback)
				log("GitHub API call failed, treating cache as stale");
				return true;
			}

			String storedUpdatedAt = readReleaseTimestamp(version, "updated_at");
			if (storedUpdatedAt == null) {
				// No stored timestamp — treat as stale (first run)
				log("No stored release timestamp, treating cache as stale");
				return true;
			}

			boolean stale = !remoteUpdatedAt.equals(storedUpdatedAt);
			log("Early-access freshness: remote=" + remoteUpdatedAt + " stored=" + storedUpdatedAt + " stale=" + stale);
			return stale;
		} catch (Exception e) {
			log("Error checking early-access freshness: " + e.getMessage());
			return true; // Treat as stale on error (safe fallback)
		}
	}

	/**
	 * Fetches the release's updated_at timestamp from the GitHub API.
	 * Uses the /repos/{owner}/{repo}/releases/tags/{tag} endpoint.
	 *
	 * @return the updated_at timestamp string, or null on failure
	 */
	private String fetchReleaseUpdatedAt(String version) {
		try {
			String apiUrl = GITHUB_API_URL + "tags/" + version;
			log("Fetching release metadata from: " + apiUrl);

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(apiUrl))
					.header("Accept", "application/vnd.github+json")
					.GET()
					.timeout(Duration.ofSeconds(10))
					.build();

			HttpResponse<String> response = httpClient.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() != 200) {
				log("GitHub API returned HTTP " + response.statusCode());
				return null;
			}

			// Parse JSON response using avaje jsonb
			GitHubRelease release = Jsonb.builder().failOnUnknown(false).build()
					.type(GitHubRelease.class).fromJson(response.body());
			if (release.updated_at() != null) {
				return release.updated_at();
			}
			log("Could not parse updated_at from API response");
			return null;
		} catch (Exception e) {
			log("Error fetching release metadata: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Reads a metadata value from the .release-info file for a given version.
	 * The file stores key=value pairs, one per line.
	 *
	 * @param version the version tag
	 * @param key     the metadata key (e.g., "updated_at", "last_check")
	 * @return the value, or null if not found
	 */
	private String readReleaseTimestamp(String version, String key) {
		try {
			Path infoFile = getVersionDir(version).resolve(RELEASE_INFO_FILE);
			if (!Files.exists(infoFile)) {
				return null;
			}
			String prefix = key + "=";
			for (String line : Files.readAllLines(infoFile, StandardCharsets.UTF_8)) {
				if (line.startsWith(prefix)) {
					return line.substring(prefix.length()).strip();
				}
			}
			return null;
		} catch (Exception e) {
			log("Error reading release metadata: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Writes a metadata key=value pair to the .release-info file.
	 * Preserves existing entries and updates/adds the given key.
	 */
	private void writeReleaseTimestamp(String version, String key, String value) {
		try {
			Path infoFile = getVersionDir(version).resolve(RELEASE_INFO_FILE);
			Map<String, String> entries = new LinkedHashMap<>();

			// Read existing entries
			if (Files.exists(infoFile)) {
				for (String line : Files.readAllLines(infoFile, StandardCharsets.UTF_8)) {
					String lineStrip = line.strip();
					if (lineStrip.isEmpty()) continue;
					int eqIdx = lineStrip.indexOf('=');
					if (eqIdx > 0) {
						entries.put(lineStrip.substring(0, eqIdx), lineStrip.substring(eqIdx + 1).strip());
					}
				}
			}

			// Update or add the key
			entries.put(key, value);

			// Write back
			StringBuilder sb = new StringBuilder();
			for (var entry : entries.entrySet()) {
				sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
			}
			Files.writeString(infoFile, sb.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (Exception e) {
			log("Error writing release metadata: " + e.getMessage());
		}
	}

	/**
	 * Validates version and type
	 */
	private void validateVersion(String version, ArtifactType type) {
		if (version == null || version.isBlank()) {
			throw new IllegalArgumentException("Version cannot be empty");
		}
		if (type == null) {
			throw new IllegalArgumentException("ArtifactType cannot be null");
		}
	}

	/**
	 * Gets the cached artifact path for a version and type
	 */
	private Path getCachedArtifactPath(String version, ArtifactType type) {
		String artifactFileName = ARTIFACT_FILE_NAMES.get(type);
		return getVersionDir(version).resolve(artifactFileName);
	}

	/**
	 * Gets the cached checksum path
	 */
	private Path getChecksumPath(String version, ArtifactType type) {
		String artifactFileName = ARTIFACT_FILE_NAMES.get(type);
		return getVersionDir(version).resolve(artifactFileName + ".sha256");
	}

	/**
	 * Gets the version directory
	 */
	private Path getVersionDir(String version) {
		return cacheBasePath.resolve(version);
	}

	/**
	 * Gets the cache base directory
	 */
	public Path getCacheBasePath() {
		return cacheBasePath;
	}

	/**
	 * Lists all cached versions
	 */
	public List<String> listCachedVersions() {
		List<String> versions = new ArrayList<>();

		if (!Files.exists(cacheBasePath)) {
			return versions;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheBasePath)) {
			for (Path entry : stream) {
				if (Files.isDirectory(entry)) {
					versions.add(entry.getFileName().toString());
				}
			}
		} catch (Exception e) {
			log("Error listing versions: " + e.getMessage());
		}

		Collections.sort(versions);
		return versions;
	}

	/**
	 * Checks if a version is cached
	 */
	public boolean isVersionCached(String version, ArtifactType type) {
		return Files.exists(getCachedArtifactPath(version, type)) && verifyCachedChecksum(version, type);
	}

	/**
	 * Clears cache for a specific version
	 */
	public void clearVersion(String version) {
		Path versionDir = getVersionDir(version);

		try {
			if (Files.exists(versionDir)) {
				// Delete all files in version directory
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionDir)) {
					for (Path file : stream) {
						Files.deleteIfExists(file);
					}
				}
				Files.deleteIfExists(versionDir);
				log("Cleared cache for version: " + version);
			}
		} catch (Exception e) {
			log("Error clearing cache for " + version + ": " + e.getMessage());
		}
	}

	/**
	 * Clears entire cache
	 */
	public void clearAll() {
		for (String version : listCachedVersions()) {
			clearVersion(version);
		}
		log("Cleared all cached artifacts");
	}

	/**
	 * Gets cache statistics
	 */
	public Map<String, Object> getStats() {
		Map<String, Object> stats = new HashMap<>();
		List<String> versions = listCachedVersions();

		stats.put("versionCount", versions.size());
		stats.put("versions", versions);
		stats.put("cachePath", cacheBasePath.toString());

		// Calculate total size
		long totalSize = 0;
		if (Files.exists(cacheBasePath)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheBasePath)) {
				for (Path versionDir : stream) {
					if (Files.isDirectory(versionDir)) {
						try (DirectoryStream<Path> files = Files.newDirectoryStream(versionDir)) {
							for (Path file : files) {
								if (!file.toString().endsWith(".sha256")) {
									totalSize += Files.size(file);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				log("Error calculating size: " + e.getMessage());
			}
		}

		stats.put("totalSizeBytes", totalSize);
		stats.put("totalSizeMB", String.format("%.2f", totalSize / (1024.0 * 1024.0)));

		return stats;
	}

	private void log(String message) {
		logger.accept(message);
	}

	/** Lightweight type for deserializing GitHub release API response with avaje jsonb. */
	@io.avaje.jsonb.Json
	record GitHubRelease(String updated_at) {
	}
}