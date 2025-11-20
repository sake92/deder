package ba.sake.deder.config;

import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import java.util.Objects;
import org.pkl.config.java.mapper.Named;
import org.pkl.config.java.mapper.NonNull;

public final class DederProject {
  public final @NonNull List<? extends @NonNull DederModule> modules;

  public DederProject(@Named("modules") @NonNull List<? extends @NonNull DederModule> modules) {
    this.modules = modules;
  }

  public DederProject withModules(@NonNull List<? extends @NonNull DederModule> modules) {
    return new DederProject(modules);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (this.getClass() != obj.getClass()) return false;
    DederProject other = (DederProject) obj;
    if (!Objects.equals(this.modules, other.modules)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + Objects.hashCode(this.modules);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(100);
    builder.append(DederProject.class.getSimpleName()).append(" {");
    appendProperty(builder, "modules", this.modules);
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

  public abstract static class DederModule {
    public final @NonNull String id;

    public final @NonNull List<? extends @NonNull DederModule> moduleDeps;

    protected DederModule(@Named("id") @NonNull String id,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      this.id = id;
      this.moduleDeps = moduleDeps;
    }
  }

  public static final class JavaModule extends DederModule {
    public JavaModule(@Named("id") @NonNull String id,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      super(id, moduleDeps);
    }

    public JavaModule withId(@NonNull String id) {
      return new JavaModule(id, moduleDeps);
    }

    public JavaModule withModuleDeps(@NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new JavaModule(id, moduleDeps);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      JavaModule other = (JavaModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(JavaModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaModule extends DederModule {
    public ScalaModule(@Named("id") @NonNull String id,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      super(id, moduleDeps);
    }

    public ScalaModule withId(@NonNull String id) {
      return new ScalaModule(id, moduleDeps);
    }

    public ScalaModule withModuleDeps(@NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaModule(id, moduleDeps);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaModule other = (ScalaModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(ScalaModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      builder.append("\n}");
      return builder.toString();
    }
  }
}
