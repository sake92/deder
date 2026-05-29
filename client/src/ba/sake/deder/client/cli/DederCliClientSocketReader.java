package ba.sake.deder.client.cli;

import io.avaje.jsonb.JsonType;

import java.io.*;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DederCliClientSocketReader implements Runnable {

    private final Consumer<String> logger;
    private final AtomicBoolean running;
    private final InputStream is;
    private final JsonType<ServerMessage> messageType;

    public DederCliClientSocketReader(Consumer<String> logger, AtomicBoolean running, InputStream is, JsonType<ServerMessage> messageType) {
        this.logger = logger;
        this.running = running;
        this.is = is;
        this.messageType = messageType;
    }

    @Override
    public void run() {
        try {
            readFromServer();
        } catch (InterruptedException e) {
            // exit thread normally
        } catch (ClosedByInterruptException e) {
            // when cancelled with Ctrl+C
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            running.set(false);
        }
    }

    private void readFromServer() throws InterruptedException, IOException {
        // newline delimited JSON messages
        var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String messageJson;
        SubprocessRunner subprocessRunner = null;
        Thread subprocessRunnerThread = null;
        // System.err.println("Waiting for messages from server...");
        while (running.get() && (messageJson = reader.readLine()) != null) {
            var message = messageType.fromJson(messageJson);
            // System.err.println("Received message from server: " + messageJson); // for debugging
            if (message instanceof ServerMessage.Output(String text)) {
                System.out.println(text);
            } else if (message instanceof ServerMessage.Log(var logText, var level)) {
                System.err.println(logText);
            } else if (message instanceof ServerMessage.RunSubprocess(String[] cmd, var envVars, boolean watch)) {
                if (subprocessRunnerThread != null && subprocessRunnerThread.isAlive()) {
                    logger.accept("Interrupting current subprocess...");
                    subprocessRunnerThread.interrupt();
                    subprocessRunnerThread.join();
                }
                subprocessRunner = new SubprocessRunner(cmd, envVars, logger);
                subprocessRunnerThread = Thread.ofVirtual().name("SubprocessRunner").start(subprocessRunner);
                if (!watch) {
                    subprocessRunnerThread.join();
                    running.set(false);
                    System.exit(subprocessRunner.getExitCode());
                }
                // else just let it run.. either new message will kill it, or user with CTRL+C
            } else if (message instanceof ServerMessage.Exit(int exitCode, boolean serverShuttingDown)) {
                if (subprocessRunnerThread != null && subprocessRunnerThread.isAlive()) {
                    logger.accept("Subprocess still running, not exiting...");
                } else {
                    if (serverShuttingDown) {
                        // Give the server time to release the lock and close sockets
                        // before this client process exits. The shell's && depends on
                        // this process terminating, so a new 'deder' command would start
                        // before the old server finished cleanup otherwise.
                        try { Thread.sleep(1500); } catch (InterruptedException e) { }
                    }
                    // running.set(false);
                    System.exit(exitCode);
                }
            }
        }
    }
}
