package ba.sake.deder.cli;

import java.io.IOException;

public class Main {


    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--bsp")) {
            var client = new DederBspProxyClient();
            client.start(args);
        } else {
            var client = new DederCliClient();
            client.start(args);
        }
    }
}
