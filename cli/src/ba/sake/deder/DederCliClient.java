package ba.sake.deder;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class DederCliClient {

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
            var buf = ByteBuffer.allocate(1024);
            while (channel.read(buf) != -1) {
                var message = new String(buf.array(), 0, buf.limit(), StandardCharsets.UTF_8);
                System.out.print("READ: " + message);
                buf.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
