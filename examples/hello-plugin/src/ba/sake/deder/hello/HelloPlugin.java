package ba.sake.deder.hello;

import ba.sake.deder.config.DederProject;

/**
 * Generated Java binding for HelloPlugin.pkl.
 * In production, this would be generated via pkl-codegen-java.
 */
public class HelloPlugin extends DederProject.Plugin {
    public String greeting;

    public String getGreeting() {
        return greeting;
    }
}
