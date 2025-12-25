package ba.sake.deder.client.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ServerMessage.Output.class, name = "Output"),
		@JsonSubTypes.Type(value = ServerMessage.Log.class, name = "Log"),
		@JsonSubTypes.Type(value = ServerMessage.RunSubprocess.class, name = "RunSubprocess"),
		@JsonSubTypes.Type(value = ServerMessage.Exit.class, name = "Exit") })
public sealed interface ServerMessage {

	// goes to stdout
	record Output(String text) implements ServerMessage {
	}

	// goes to stderr
	record Log(String text, LogLevel level) implements ServerMessage {
	}

	record RunSubprocess(String[] cmd, boolean watch) implements ServerMessage {
	}

	record Exit(int exitCode) implements ServerMessage {
	}

	public enum LogLevel {
		ERROR, WARNING, INFO, DEBUG, TRACE
	}
}
