package ba.sake.deder.client.cli;

import io.avaje.jsonb.Json;

@Json
@Json.SubType(type = ClientMessage.Help.class, name = "Help")
@Json.SubType(type = ClientMessage.Version.class, name = "Version")
@Json.SubType(type = ClientMessage.Modules.class, name = "Modules")
@Json.SubType(type = ClientMessage.Tasks.class, name = "Tasks")
@Json.SubType(type = ClientMessage.Plan.class, name = "Plan")
@Json.SubType(type = ClientMessage.Exec.class, name = "Exec")
@Json.SubType(type = ClientMessage.Cancel.class, name = "Cancel")
@Json.SubType(type = ClientMessage.Clean.class, name = "Clean")
@Json.SubType(type = ClientMessage.Import.class, name = "Import")
@Json.SubType(type = ClientMessage.Complete.class, name = "Complete")
@Json.SubType(type = ClientMessage.Shutdown.class, name = "Shutdown")
public sealed interface ClientMessage {

    record Help(String[] args) implements ClientMessage {
    }

    record Version() implements ClientMessage {
    }

    record Modules(String[] args) implements ClientMessage {
    }

    record Tasks(String[] args) implements ClientMessage {
    }

    record Plan(String[] args) implements ClientMessage {
    }

    record Exec(String requestId, String[] args, java.util.Map<String, String> envVars) implements ClientMessage {
    }

    record Cancel(String requestId) implements ClientMessage {
    }

    record Clean(String[] args) implements ClientMessage {
    }

    record Import(String[] args) implements ClientMessage {
    }

    record Complete(String[] args) implements ClientMessage {
    }

    record Shutdown() implements ClientMessage {
    }
}