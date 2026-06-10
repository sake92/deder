package ba.sake.deder.config;

import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import java.util.Objects;
import org.pkl.config.java.mapper.Named;
import org.pkl.config.java.mapper.NonNull;

public final class DederPlugins {
  private DederPlugins() {
  }

  private static void appendProperty(StringBuilder builder, String name, Object value) {
    builder.append("\n  ").append(name).append(" = ");
    String[] lines = Objects.toString(value).split("\n");
    builder.append(lines[0]);
    for (int i = 1; i < lines.length; i++) {
      builder.append("\n  ").append(lines[i]);
    }
  }

  public static class DederPlugin {
    public final @NonNull String id;

    public final @NonNull List<@NonNull String> deps;

    public DederPlugin(@Named("id") @NonNull String id,
        @Named("deps") @NonNull List<@NonNull String> deps) {
      this.id = id;
      this.deps = deps;
    }

    public DederPlugin withId(@NonNull String id) {
      return new DederPlugin(id, deps);
    }

    public DederPlugin withDeps(@NonNull List<@NonNull String> deps) {
      return new DederPlugin(id, deps);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      DederPlugin other = (DederPlugin) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.deps);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(DederPlugin.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "deps", this.deps);
      builder.append("\n}");
      return builder.toString();
    }
  }
}
