package ba.sake.deder.cli;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ServerMessage.PrintText.class, name = "PrintText"),
        @JsonSubTypes.Type(value = ServerMessage.RunSubprocess.class, name = "RunSubprocess"),
        @JsonSubTypes.Type(value = ServerMessage.Exit.class, name = "Exit"),
})
sealed interface ServerMessage {

    record PrintText(String text, Level level) implements ServerMessage {
    }

    record RunSubprocess(String[] cmd) implements ServerMessage {
    }

    record Exit(int exitCode) implements ServerMessage {
    }

    enum Level {
        ERROR, WARNING, INFO, DEBUG, TRACE
    }
}
