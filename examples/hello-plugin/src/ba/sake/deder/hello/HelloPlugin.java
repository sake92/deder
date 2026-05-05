package ba.sake.deder.hello;

import ba.sake.deder.config.DederProject;
import java.util.Collections;
import java.util.List;
import org.pkl.config.java.mapper.Named;

/**
 * Generated Java binding for HelloPlugin.pkl.
 * In production, this would be generated via pkl-codegen-java.
 */
public class HelloPlugin extends DederProject.Plugin {
    public String greeting;

    /** Constructor used by Pkl's ConfigEvaluator to map values via @Named parameters. */
    public HelloPlugin(@Named("id") String id,
                       @Named("deps") List<String> deps,
                       @Named("greeting") String greeting) {
        super(id, deps);
        this.greeting = greeting;
    }

    /** No-arg constructor for Java compilation. */
    public HelloPlugin() {
        this("", Collections.emptyList(), "Hello!");
    }

    public String getGreeting() {
        return greeting;
    }
}
