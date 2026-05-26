package ba.sake.deder.client.cli;

import io.avaje.jsonb.Json;

import java.util.Map;

@Json
@Json.SubType(type = ServerMessage.Output.class, name = "Output")
@Json.SubType(type = ServerMessage.Log.class, name = "Log")
@Json.SubType(type = ServerMessage.RunSubprocess.class, name = "RunSubprocess")
@Json.SubType(type = ServerMessage.Exit.class, name = "Exit")
public sealed interface ServerMessage {

	// goes to stdout
	record Output(String text) implements ServerMessage {
	}

	// goes to stderr
	record Log(String text, LogLevel level) implements ServerMessage {
	}

	record RunSubprocess(String[] cmd, Map<String, String> envVars, boolean watch) implements ServerMessage {
	}

	record Exit(int exitCode, boolean serverShuttingDown) implements ServerMessage {
	}

	enum LogLevel {
		ERROR, WARNING, INFO, DEBUG, TRACE
	}
}
