package ba.sake.deder.hello;

import ba.sake.deder.config.DederProject;

/**
 * Generated Java binding for HelloConfig.pkl.
 * In production, this would be generated via pkl-codegen-java.
 */
public class HelloConfig extends DederProject.Plugin {
    public String greeting;

    public String getGreeting() {
        return greeting;
    }
}
