package ba.sake.deder.client.cli;

import io.avaje.jsonb.JsonType;

import java.io.*;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DederCliClientReadThread extends Thread {

    private final Consumer<String> logger;
    private final AtomicBoolean running;
    private final InputStream is;
    private final JsonType<ServerMessage> messageType;

    public DederCliClientReadThread(Consumer<String> logger, AtomicBoolean running, InputStream is, JsonType<ServerMessage> messageType) {
        super("DederCliClientReadThread");
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
        SubprocessRunningThread subprocessRunningThread = null;
        // System.err.println("Waiting for messages from server...");
        while (running.get() && (messageJson = reader.readLine()) != null) {
            var message = messageType.fromJson(messageJson);
            // System.err.println("Received message from server: " + messageJson); // for debugging
            if (message instanceof ServerMessage.Output(String text)) {
                System.out.println(text);
            } else if (message instanceof ServerMessage.Log(var logText, var level)) {
                System.err.println(logText);
            } else if (message instanceof ServerMessage.RunSubprocess(String[] cmd, var envVars, boolean watch)) {
                if (subprocessRunningThread != null && subprocessRunningThread.isAlive()) {
                    logger.accept("Interrupting current subprocess...");
                    subprocessRunningThread.interrupt();
                    subprocessRunningThread.join();
                }
                subprocessRunningThread = new SubprocessRunningThread(cmd, envVars, logger);
                subprocessRunningThread.start();
                if (!watch) {
                    subprocessRunningThread.join();
                    running.set(false);
                    System.exit(subprocessRunningThread.getExitCode());
                }
                // else just let it run.. either new message will kill it, or user with CTRL+C
            } else if (message instanceof ServerMessage.Exit(int exitCode, boolean serverShuttingDown)) {
                if (subprocessRunningThread != null && subprocessRunningThread.isAlive()) {
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
