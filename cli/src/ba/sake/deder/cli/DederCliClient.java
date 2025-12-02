package ba.sake.deder.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

// TODO handle color stuff https://clig.dev/#output

public class DederCliClient {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    // TODO in BSP mode it would run forever, listening to server messages -> forwarding stdout
    // and forwarding stdin to server

    // TODO the server needs to know that it's a BSP client communication

    public void start(String[] args) throws IOException {
        var socketPath = Path.of(".deder/server.sock");
        var address = UnixDomainSocketAddress.of(socketPath);
        //System.out.println("Connecting to server...");
        try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(address);
            //System.out.println("Connected with server!");
            Thread serverWriteThread = new Thread(() -> {
                try {
                    serverWrite(channel, args);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, "write-to-server");
            Thread serverReadThread = new Thread(() -> {
                try {
                    serverRead(channel);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, "read-from-server");
            serverWriteThread.start();
            serverReadThread.start();
            serverWriteThread.join();
            serverReadThread.join();
            //System.out.println("Server disconnected"); // channel.read == -1
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void serverWrite(SocketChannel channel, String[] args) throws IOException {
        //while (true) {
        // newline delimited JSON messages
        var message = new ClientMessage.Run(args);
        var messageJson = jsonMapper.writeValueAsString(message);
        var buf = ByteBuffer.wrap((messageJson + '\n').getBytes(StandardCharsets.UTF_8));
        channel.write(buf);
        //}
    }

    void serverRead(SocketChannel channel) throws IOException {
        // newline delimited JSON messages
        var buf = ByteBuffer.allocate(1024);
        var messageOS = new ByteArrayOutputStream(1024);
        while (channel.read(buf) != -1) {
            buf.flip();
            while (buf.hasRemaining()) {
                byte c = buf.get();
                if (c == '\n') {
                    var messageJson = messageOS.toString(StandardCharsets.UTF_8);
                    var message = jsonMapper.readValue(messageJson, ServerMessage.class);
                    switch (message) {
                        case ServerMessage.Output(var text) -> {
                            System.out.println(text);
                        }
                        case ServerMessage.Log(var text, var level) -> {
                            System.err.println(text);
                        }
                        case ServerMessage.RunSubprocess runSubprocess -> {
                            ProcessBuilder processBuilder = new ProcessBuilder(runSubprocess.cmd());
                            processBuilder.inheritIO();
                            Process process = processBuilder.start();
                            try {
                                process.waitFor();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        case ServerMessage.Exit(int exitCode) -> System.exit(exitCode); // TODO cleanup
                    }
                    messageOS = new ByteArrayOutputStream(1024);
                } else {
                    messageOS.write(c);
                }
            }
            buf.clear();
        }
    }

}
