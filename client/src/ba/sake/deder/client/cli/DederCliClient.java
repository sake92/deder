package ba.sake.deder.client.cli;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import ba.sake.deder.client.DederClient;

// TODO handle color stuff https://clig.dev/#output

public class DederCliClient implements DederClient {

    private final String[] args;
    private final Path logFile;
    private Thread serverWriteThread;
    private Thread serverReadThread;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private BlockingQueue<ClientMessage> clientMessages;
    private String requestId;

    public DederCliClient(String[] args, Path logFile) {
        this.args = args;
        this.logFile = logFile;
    }

    @Override
    public void start() throws Exception {
        running.set(true);
        requestId = UUID.randomUUID().toString();
        clientMessages = new LinkedBlockingQueue<>();
        var socketPath = Path.of(".deder/server-cli.sock");
        var address = UnixDomainSocketAddress.of(socketPath);
        try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(address);
            var os = Channels.newOutputStream(channel);
            var is = Channels.newInputStream(channel);
            serverWriteThread = new Thread(() -> {
                try {
                    serverWrite(os, clientMessages);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                }
            }, "DederCliServerWriteThread");
            serverReadThread = new Thread(() -> {
                try {
                    serverRead(is);
                } catch (InterruptedException e) {
                    // exit thread normally
                } catch (ClosedByInterruptException e) {
                    // when cancelled with Ctrl+C
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, "DederCliServerReadThread");
            serverReadThread.start();
            serverWriteThread.start();
            ClientMessage message;
            if (args.length > 0) {
                var leftoverArgs = new String[args.length - 1];
                System.arraycopy(args, 1, leftoverArgs, 0, args.length - 1);
                message = switch (args[0]) {
                    case "version" -> {
                        // TODO send it to server? for formatting or whatevs/consistency
                        System.out.println("Client version: 0.0.1");
                        yield new ClientMessage.Version();
                    }
                    case "clean" -> new ClientMessage.Clean(leftoverArgs);
                    case "modules" -> new ClientMessage.Modules(leftoverArgs);
                    case "tasks" -> new ClientMessage.Tasks(leftoverArgs);
                    case "plan" -> new ClientMessage.Plan(leftoverArgs);
                    case "exec" -> new ClientMessage.Exec(requestId, leftoverArgs);
                    case "shutdown" -> new ClientMessage.Shutdown();
                    case "import" -> new ClientMessage.Import(leftoverArgs);
                    case "help", "--help", "-h" -> new ClientMessage.Help(leftoverArgs);
                    default -> new ClientMessage.Help(args);
                };
            } else {
                message = new ClientMessage.Help(args);
            }
            clientMessages.add(message);
            serverReadThread.join();
            serverWriteThread.join();
            log("Server disconnected"); // channel.read == -1
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop(boolean isCancel) throws Exception {
        if (isCancel) {
            log("Cancellation requested, sending cancel message to server...");
            clientMessages.add(new ClientMessage.Cancel(requestId));
            // wait a bit for the message to be sent
            int maxWaitMs = 500;
            int waitedMs = 0;
            while (!clientMessages.isEmpty() && waitedMs < maxWaitMs) {
                Thread.sleep(50);
                waitedMs += 50;
            }
            Thread.sleep(3000); // wait a bit for server's response
        }
        log("Setting running to false, stopping client threads...");
        running.set(false);
        if (serverWriteThread != null) {
            //    serverWriteThread.interrupt();
            serverWriteThread.join(1000);
        }
        if (serverReadThread != null) {
            serverReadThread.interrupt();
            serverReadThread.join(1000);
        }
    }

    private void serverWrite(OutputStream os, BlockingQueue<ClientMessage> clientMessages) throws IOException, InterruptedException {
        while (running.get()) {
            var message = clientMessages.poll(100, TimeUnit.MILLISECONDS);
            if (message == null) {
                continue;
            }
            // newline delimited JSON messages
            var messageJson = jsonMapper.writeValueAsString(message);
            log("Sending message to server: " + messageJson);
            os.write((messageJson + '\n').getBytes(StandardCharsets.UTF_8));
            log("Sent message to server");
        }
    }

    private void serverRead(InputStream is) throws InterruptedException, IOException {
        // newline delimited JSON messages
        var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String messageJson;
        SubprocessRunningThread subprocessRunningThread = null;
        // System.err.println("Waiting for messages from server...");

        while (running.get() && (messageJson = reader.readLine()) != null) {
            var message = jsonMapper.readValue(messageJson, ServerMessage.class);
            // System.err.println("Received message from server: " + messageJson); // for debugging
            if (message instanceof ServerMessage.Output output) {
                System.out.println(output.text());
            } else if (message instanceof ServerMessage.Log log) {
                System.err.println(log.text());
            } else if (message instanceof ServerMessage.RunSubprocess runSubprocess) {
                if (subprocessRunningThread != null && subprocessRunningThread.isAlive()) {
                    log("Interrupting current subprocess...");
                    subprocessRunningThread.interrupt();
                    subprocessRunningThread.join();
                }
                subprocessRunningThread = new SubprocessRunningThread(runSubprocess.cmd(), this::log);
                subprocessRunningThread.start();
                if (!runSubprocess.watch()) {
                    subprocessRunningThread.join();
                    running.set(false);
                    System.exit(subprocessRunningThread.getExitCode());
                }
                // else just let it run.. either new message will kill it, or user with CTRL+C
            } else if (message instanceof ServerMessage.Exit exit) {
                if (subprocessRunningThread != null && subprocessRunningThread.isAlive()) {
                    // TODO is this logic sound? :/
                    log("Subprocess still running, not exiting...");
                } else {
                    // running.set(false);
                    System.exit(exit.exitCode());
                }
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
