package ba.sake.deder.hello;

import ba.sake.deder.config.DederProject;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import java.util.Objects;
import org.pkl.config.java.mapper.Named;
import org.pkl.config.java.mapper.NonNull;

public final class Hello {
  public final @NonNull HelloPluginConfig config;

  public Hello(@Named("config") @NonNull HelloPluginConfig config) {
    this.config = config;
  }

  public Hello withConfig(@NonNull HelloPluginConfig config) {
    return new Hello(config);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (this.getClass() != obj.getClass()) return false;
    Hello other = (Hello) obj;
    if (!Objects.equals(this.config, other.config)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + Objects.hashCode(this.config);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(100);
    builder.append(Hello.class.getSimpleName()).append(" {");
    appendProperty(builder, "config", this.config);
    builder.append("\n}");
    return builder.toString();
  }

  private static void appendProperty(StringBuilder builder, String name, Object value) {
    builder.append("\n  ").append(name).append(" = ");
    String[] lines = Objects.toString(value).split("\n");
    builder.append(lines[0]);
    for (int i = 1; i < lines.length; i++) {
      builder.append("\n  ").append(lines[i]);
    }
  }

  public static final class HelloPlugin extends DederProject.DederPlugin {
    public final @NonNull HelloPluginConfig config;

    public HelloPlugin(@Named("id") @NonNull String id,
        @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("config") @NonNull HelloPluginConfig config) {
      super(id, deps);
      this.config = config;
    }

    public HelloPlugin withId(@NonNull String id) {
      return new HelloPlugin(id, deps, config);
    }

    public HelloPlugin withDeps(@NonNull List<@NonNull String> deps) {
      return new HelloPlugin(id, deps, config);
    }

    public HelloPlugin withConfig(@NonNull HelloPluginConfig config) {
      return new HelloPlugin(id, deps, config);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      HelloPlugin other = (HelloPlugin) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.config, other.config)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.config);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(HelloPlugin.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "config", this.config);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class HelloPluginConfig {
    public final @NonNull String greeting;

    public HelloPluginConfig(@Named("greeting") @NonNull String greeting) {
      this.greeting = greeting;
    }

    public HelloPluginConfig withGreeting(@NonNull String greeting) {
      return new HelloPluginConfig(greeting);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      HelloPluginConfig other = (HelloPluginConfig) obj;
      if (!Objects.equals(this.greeting, other.greeting)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.greeting);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(100);
      builder.append(HelloPluginConfig.class.getSimpleName()).append(" {");
      appendProperty(builder, "greeting", this.greeting);
      builder.append("\n}");
      return builder.toString();
    }
  }
}
