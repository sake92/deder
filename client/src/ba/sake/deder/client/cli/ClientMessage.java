package ba.sake.deder.client.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ClientMessage.Help.class, name = "Help"),
		@JsonSubTypes.Type(value = ClientMessage.Version.class, name = "Version"),
		@JsonSubTypes.Type(value = ClientMessage.Modules.class, name = "Modules"),
		@JsonSubTypes.Type(value = ClientMessage.Tasks.class, name = "Tasks"),
		@JsonSubTypes.Type(value = ClientMessage.Plan.class, name = "Plan"),
        @JsonSubTypes.Type(value = ClientMessage.Exec.class, name = "Exec"),
        @JsonSubTypes.Type(value = ClientMessage.Clean.class, name = "Clean"),
        @JsonSubTypes.Type(value = ClientMessage.Import.class, name = "Import"),
		@JsonSubTypes.Type(value = ClientMessage.Shutdown.class, name = "Shutdown") })
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

	record Exec(String[] args) implements ClientMessage {
	}

	record Clean(String[] args) implements ClientMessage {
	}

	record Import(String[] args) implements ClientMessage {
	}

	record Shutdown() implements ClientMessage {
	}
}