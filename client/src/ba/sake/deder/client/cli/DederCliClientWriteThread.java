package ba.sake.deder.client.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DederCliClientWriteThread extends Thread {

    private final Consumer<String> logger;
    private final AtomicBoolean running;
    private final OutputStream os;
    private final BlockingQueue<ClientMessage> clientMessages;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public DederCliClientWriteThread(Consumer<String> logger, AtomicBoolean running, OutputStream os, BlockingQueue<ClientMessage> clientMessages) {
        super("DederCliClientWriteThread");
        this.logger = logger;
        this.running = running;
        this.os = os;
        this.clientMessages = clientMessages;
    }

    @Override
    public void run() {
        try {
            writeToServer();
        } catch (InterruptedException e) {
            // exit thread normally
        } catch (ClosedByInterruptException e) {
            // when cancelled with Ctrl+C
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeToServer() throws IOException, InterruptedException {
        while (running.get()) {
            var message = clientMessages.poll(100, TimeUnit.MILLISECONDS);
            if (message == null) {
                continue;
            }
            // newline delimited JSON messages
            var messageJson = jsonMapper.writeValueAsString(message);
            logger.accept("Sending message to server: " + messageJson);
            os.write((messageJson + '\n').getBytes(StandardCharsets.UTF_8));
            logger.accept("Sent message to server");
        }
    }
}
