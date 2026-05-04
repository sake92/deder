package ba.sake.deder.hello;

import ba.sake.deder.config.DederProject;
import java.util.Collections;

/**
 * Generated Java binding for HelloConfig.pkl.
 * In production, this would be generated via pkl-codegen-java.
 */
public class HelloConfig extends DederProject.Plugin {
    public String greeting;

    /** Required for Java compilation. Pkl populates fields via its own mechanism. */
    public HelloConfig() {
        super("", Collections.emptyList());
    }

    public String getGreeting() {
        return greeting;
    }
}
