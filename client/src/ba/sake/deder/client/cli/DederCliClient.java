package ba.sake.deder.client.cli;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import ba.sake.deder.client.DederClient;

// TODO handle color stuff https://clig.dev/#output

public class DederCliClient implements DederClient {

    private final Consumer<String> logger;
    private final String[] args;
    private Thread writeThread;
    private Thread readThread;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private BlockingQueue<ClientMessage> clientMessages;
    private String requestId;

    public DederCliClient(Consumer<String> logger, String[] args) {
        this.logger = logger;
        this.args = args;
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
            writeThread = new DederCliClientWriteThread(logger, running, os, clientMessages);
            readThread = new DederCliClientReadThread(logger, running, is);
            readThread.start();
            writeThread.start();
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
                    case "complete" -> new ClientMessage.Complete(leftoverArgs);
                    case "help", "--help", "-h" -> new ClientMessage.Help(leftoverArgs);
                    default -> new ClientMessage.Help(args);
                };
            } else {
                message = new ClientMessage.Help(args);
            }
            clientMessages.add(message);
            readThread.join();
            writeThread.join();
            logger.accept("Server disconnected");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop(boolean isCancel) throws Exception {
        if (isCancel) {
            logger.accept("Cancel requested, sending cancel message to server...");
            clientMessages.add(new ClientMessage.Cancel(requestId));
            // wait a bit for the message to be sent
            int maxWaitMs = 500;
            int waitedMs = 0;
            while (!clientMessages.isEmpty() && waitedMs < maxWaitMs) {
                Thread.sleep(50);
                waitedMs += 50;
            }
            Thread.sleep(200); // wait a bit for server's response
        }
        // logger.accept("Setting running to false, stopping client threads...");
        running.set(false);
        if (writeThread != null) {
            //    writeThread.interrupt();
            writeThread.join(1000);
        }
        if (readThread != null) {
            readThread.interrupt();
            readThread.join(1000);
        }
    }
}
