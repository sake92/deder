package ba.sake.deder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
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

public class DederCliClient {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        var client = new DederCliClient();
        client.start(args);
    }

    public void start(String[] args) throws IOException {
        var socketPath = Path.of(".deder/cli.sock");
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
                        case ServerMessage.PrintText(var text, var level) -> {
                            if (level != ServerMessage.Level.DEBUG) {
                                System.out.println(text);
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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
sealed interface ClientMessage {
    @JsonTypeName("Run")
    record Run(String[] args) implements ClientMessage {
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")

@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerMessage.PrintText.class, name = "PrintText"),
        @JsonSubTypes.Type(value = ServerMessage.Exit.class, name = "Exit"),
})
sealed interface ServerMessage {
    record PrintText(String text, Level level) implements ServerMessage {
    }

    record Exit(int exitCode) implements ServerMessage {
    }

    enum Level {
        ERROR, WARNING, INFO, DEBUG;
    }
}
