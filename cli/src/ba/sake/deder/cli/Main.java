package ba.sake.deder.cli;

import java.io.IOException;

public class Main {


    public static void main(String[] args) throws IOException {
        // TODO pass in debug level
        var client = new DederCliClient();
        client.start(args);
    }
}
