package ba.sake.deder.client.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ClientMessage.Run.class, name = "Run") })
public sealed interface ClientMessage {

	// TODO a handshake message? pass log level etc

	record Run(String[] args) implements ClientMessage {
	}
}