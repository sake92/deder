package ba.sake.deder;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        client.start();
    }

    public void start() throws IOException {
        var socketPath = Path.of(".deder/cli.sock");
        var address = UnixDomainSocketAddress.of(socketPath);
        System.out.println("Connecting to server...");
        try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(address);
            System.out.println("Connected with server!");
            // newline delimited JSON messages
            var buf = ByteBuffer.allocate(1024);
            var messageOS = new ByteArrayOutputStream(1024);
            while (channel.read(buf) != -1) {
                buf.flip();
                while (buf.hasRemaining()) {
                    byte c = buf.get();
                    if (c == '\n') {
                        var messageJson = messageOS.toString(StandardCharsets.UTF_8);
                        PrintText message = jsonMapper.readValue(messageJson, PrintText.class);
                        System.out.println("GOT FULL MESSAGE: " + message);
                        messageOS = new ByteArrayOutputStream(1024);
                    } else {
                        messageOS.write(c);
                    }
                }
                buf.clear();
            }
            System.out.println("Server disconnected"); // channel.read == -1
        }
    }
}

record PrintText(String text) {
}
