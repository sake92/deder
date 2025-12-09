package ba.sake.deder.client.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ClientMessage.Run.class, name = "Run"),
		@JsonSubTypes.Type(value = ClientMessage.Shutdown.class, name = "Shutdown") })
public sealed interface ClientMessage {

	record Run(String[] args) implements ClientMessage {
	}

	record Shutdown() implements ClientMessage {
	}
}