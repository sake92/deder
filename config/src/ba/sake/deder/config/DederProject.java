package ba.sake.deder.config;

import java.lang.Boolean;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.pkl.config.java.mapper.Named;
import org.pkl.config.java.mapper.NonNull;

public final class DederProject {
  public final @NonNull List<? extends @NonNull DederModule> modules;

  public final @NonNull List<? extends DederPlugins. @NonNull DederPlugin> plugins;

  public final @NonNull List<@NonNull MavenRepository> repositories;

  public final boolean includeDefaultRepos;

  public final @NonNull List<@NonNull String> watchIgnore;

  public final boolean bspEnabled;

  public final long maxActiveCompilers;

  public final long maxConcurrentTestForks;

  public final @NonNull Map<@NonNull String, ? extends @NonNull ServerTool> tools;

  public DederProject(@Named("modules") @NonNull List<? extends @NonNull DederModule> modules,
      @Named("plugins") @NonNull List<? extends DederPlugins. @NonNull DederPlugin> plugins,
      @Named("repositories") @NonNull List<@NonNull MavenRepository> repositories,
      @Named("includeDefaultRepos") boolean includeDefaultRepos,
      @Named("watchIgnore") @NonNull List<@NonNull String> watchIgnore,
      @Named("bspEnabled") boolean bspEnabled, @Named("maxActiveCompilers") long maxActiveCompilers,
      @Named("maxConcurrentTestForks") long maxConcurrentTestForks,
      @Named("tools") @NonNull Map<@NonNull String, ? extends @NonNull ServerTool> tools) {
    this.modules = modules;
    this.plugins = plugins;
    this.repositories = repositories;
    this.includeDefaultRepos = includeDefaultRepos;
    this.watchIgnore = watchIgnore;
    this.bspEnabled = bspEnabled;
    this.maxActiveCompilers = maxActiveCompilers;
    this.maxConcurrentTestForks = maxConcurrentTestForks;
    this.tools = tools;
  }

  public DederProject withModules(@NonNull List<? extends @NonNull DederModule> modules) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withPlugins(
      @NonNull List<? extends DederPlugins. @NonNull DederPlugin> plugins) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withRepositories(@NonNull List<@NonNull MavenRepository> repositories) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withIncludeDefaultRepos(boolean includeDefaultRepos) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withWatchIgnore(@NonNull List<@NonNull String> watchIgnore) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withBspEnabled(boolean bspEnabled) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withMaxActiveCompilers(long maxActiveCompilers) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withMaxConcurrentTestForks(long maxConcurrentTestForks) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  public DederProject withTools(
      @NonNull Map<@NonNull String, ? extends @NonNull ServerTool> tools) {
    return new DederProject(modules, plugins, repositories, includeDefaultRepos, watchIgnore, bspEnabled, maxActiveCompilers, maxConcurrentTestForks, tools);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (this.getClass() != obj.getClass()) return false;
    DederProject other = (DederProject) obj;
    if (!Objects.equals(this.modules, other.modules)) return false;
    if (!Objects.equals(this.plugins, other.plugins)) return false;
    if (!Objects.equals(this.repositories, other.repositories)) return false;
    if (!Objects.equals(this.includeDefaultRepos, other.includeDefaultRepos)) return false;
    if (!Objects.equals(this.watchIgnore, other.watchIgnore)) return false;
    if (!Objects.equals(this.bspEnabled, other.bspEnabled)) return false;
    if (!Objects.equals(this.maxActiveCompilers, other.maxActiveCompilers)) return false;
    if (!Objects.equals(this.maxConcurrentTestForks, other.maxConcurrentTestForks)) return false;
    if (!Objects.equals(this.tools, other.tools)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + Objects.hashCode(this.modules);
    result = 31 * result + Objects.hashCode(this.plugins);
    result = 31 * result + Objects.hashCode(this.repositories);
    result = 31 * result + Objects.hashCode(this.includeDefaultRepos);
    result = 31 * result + Objects.hashCode(this.watchIgnore);
    result = 31 * result + Objects.hashCode(this.bspEnabled);
    result = 31 * result + Objects.hashCode(this.maxActiveCompilers);
    result = 31 * result + Objects.hashCode(this.maxConcurrentTestForks);
    result = 31 * result + Objects.hashCode(this.tools);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(500);
    builder.append(DederProject.class.getSimpleName()).append(" {");
    appendProperty(builder, "modules", this.modules);
    appendProperty(builder, "plugins", this.plugins);
    appendProperty(builder, "repositories", this.repositories);
    appendProperty(builder, "includeDefaultRepos", this.includeDefaultRepos);
    appendProperty(builder, "watchIgnore", this.watchIgnore);
    appendProperty(builder, "bspEnabled", this.bspEnabled);
    appendProperty(builder, "maxActiveCompilers", this.maxActiveCompilers);
    appendProperty(builder, "maxConcurrentTestForks", this.maxConcurrentTestForks);
    appendProperty(builder, "tools", this.tools);
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

  public abstract static class PublishRepo {
    public final @NonNull String id;

    protected PublishRepo(@Named("id") @NonNull String id) {
      this.id = id;
    }
  }

  public static final class SonatypeCentralRepo extends PublishRepo {
    public SonatypeCentralRepo(@Named("id") @NonNull String id) {
      super(id);
    }

    public SonatypeCentralRepo withId(@NonNull String id) {
      return new SonatypeCentralRepo(id);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      SonatypeCentralRepo other = (SonatypeCentralRepo) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(100);
      builder.append(SonatypeCentralRepo.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class SonatypeSnapshotRepo extends PublishRepo {
    public SonatypeSnapshotRepo(@Named("id") @NonNull String id) {
      super(id);
    }

    public SonatypeSnapshotRepo withId(@NonNull String id) {
      return new SonatypeSnapshotRepo(id);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      SonatypeSnapshotRepo other = (SonatypeSnapshotRepo) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(100);
      builder.append(SonatypeSnapshotRepo.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class MavenRepo extends PublishRepo {
    public final @NonNull String url;

    public MavenRepo(@Named("id") @NonNull String id, @Named("url") @NonNull String url) {
      super(id);
      this.url = url;
    }

    public MavenRepo withId(@NonNull String id) {
      return new MavenRepo(id, url);
    }

    public MavenRepo withUrl(@NonNull String url) {
      return new MavenRepo(id, url);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      MavenRepo other = (MavenRepo) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.url, other.url)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.url);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(MavenRepo.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "url", this.url);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class MavenRepository {
    public final @NonNull String url;

    public MavenRepository(@Named("url") @NonNull String url) {
      this.url = url;
    }

    public MavenRepository withUrl(@NonNull String url) {
      return new MavenRepository(url);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      MavenRepository other = (MavenRepository) obj;
      if (!Objects.equals(this.url, other.url)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.url);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(100);
      builder.append(MavenRepository.class.getSimpleName()).append(" {");
      appendProperty(builder, "url", this.url);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public abstract static class DederModule {
    public final @NonNull String id;

    public final @NonNull String root;

    public final @NonNull List<@NonNull String> sources;

    public final @NonNull List<? extends @NonNull DederModule> moduleDeps;

    public final Boolean bspVisible;

    public final @NonNull ModuleType type;

    protected DederModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type) {
      this.id = id;
      this.root = root;
      this.sources = sources;
      this.moduleDeps = moduleDeps;
      this.bspVisible = bspVisible;
      this.type = type;
    }
  }

  public static final class PomLicense {
    public final @NonNull String name;

    public final @NonNull String url;

    public PomLicense(@Named("name") @NonNull String name, @Named("url") @NonNull String url) {
      this.name = name;
      this.url = url;
    }

    public PomLicense withName(@NonNull String name) {
      return new PomLicense(name, url);
    }

    public PomLicense withUrl(@NonNull String url) {
      return new PomLicense(name, url);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      PomLicense other = (PomLicense) obj;
      if (!Objects.equals(this.name, other.name)) return false;
      if (!Objects.equals(this.url, other.url)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.name);
      result = 31 * result + Objects.hashCode(this.url);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(PomLicense.class.getSimpleName()).append(" {");
      appendProperty(builder, "name", this.name);
      appendProperty(builder, "url", this.url);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class PomDeveloper {
    public final @NonNull String id;

    public final @NonNull String name;

    public final @NonNull String email;

    public PomDeveloper(@Named("id") @NonNull String id, @Named("name") @NonNull String name,
        @Named("email") @NonNull String email) {
      this.id = id;
      this.name = name;
      this.email = email;
    }

    public PomDeveloper withId(@NonNull String id) {
      return new PomDeveloper(id, name, email);
    }

    public PomDeveloper withName(@NonNull String name) {
      return new PomDeveloper(id, name, email);
    }

    public PomDeveloper withEmail(@NonNull String email) {
      return new PomDeveloper(id, name, email);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      PomDeveloper other = (PomDeveloper) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.name, other.name)) return false;
      if (!Objects.equals(this.email, other.email)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.name);
      result = 31 * result + Objects.hashCode(this.email);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(PomDeveloper.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "name", this.name);
      appendProperty(builder, "email", this.email);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class PomScm {
    public final String url;

    public final String connection;

    public final String developerConnection;

    public final String tag;

    public PomScm(@Named("url") String url, @Named("connection") String connection,
        @Named("developerConnection") String developerConnection, @Named("tag") String tag) {
      this.url = url;
      this.connection = connection;
      this.developerConnection = developerConnection;
      this.tag = tag;
    }

    public PomScm withUrl(String url) {
      return new PomScm(url, connection, developerConnection, tag);
    }

    public PomScm withConnection(String connection) {
      return new PomScm(url, connection, developerConnection, tag);
    }

    public PomScm withDeveloperConnection(String developerConnection) {
      return new PomScm(url, connection, developerConnection, tag);
    }

    public PomScm withTag(String tag) {
      return new PomScm(url, connection, developerConnection, tag);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      PomScm other = (PomScm) obj;
      if (!Objects.equals(this.url, other.url)) return false;
      if (!Objects.equals(this.connection, other.connection)) return false;
      if (!Objects.equals(this.developerConnection, other.developerConnection)) return false;
      if (!Objects.equals(this.tag, other.tag)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.url);
      result = 31 * result + Objects.hashCode(this.connection);
      result = 31 * result + Objects.hashCode(this.developerConnection);
      result = 31 * result + Objects.hashCode(this.tag);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(250);
      builder.append(PomScm.class.getSimpleName()).append(" {");
      appendProperty(builder, "url", this.url);
      appendProperty(builder, "connection", this.connection);
      appendProperty(builder, "developerConnection", this.developerConnection);
      appendProperty(builder, "tag", this.tag);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class PomSettings {
    public final @NonNull String groupId;

    public final @NonNull String artifactId;

    public final String version;

    public final @NonNull String name;

    public final String description;

    public final String url;

    public final @NonNull List<@NonNull PomLicense> licenses;

    public final @NonNull List<@NonNull PomDeveloper> developers;

    public final PomScm scm;

    public PomSettings(@Named("groupId") @NonNull String groupId,
        @Named("artifactId") @NonNull String artifactId, @Named("version") String version,
        @Named("name") @NonNull String name, @Named("description") String description,
        @Named("url") String url, @Named("licenses") @NonNull List<@NonNull PomLicense> licenses,
        @Named("developers") @NonNull List<@NonNull PomDeveloper> developers,
        @Named("scm") PomScm scm) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.name = name;
      this.description = description;
      this.url = url;
      this.licenses = licenses;
      this.developers = developers;
      this.scm = scm;
    }

    public PomSettings withGroupId(@NonNull String groupId) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withArtifactId(@NonNull String artifactId) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withVersion(String version) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withName(@NonNull String name) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withDescription(String description) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withUrl(String url) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withLicenses(@NonNull List<@NonNull PomLicense> licenses) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withDevelopers(@NonNull List<@NonNull PomDeveloper> developers) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    public PomSettings withScm(PomScm scm) {
      return new PomSettings(groupId, artifactId, version, name, description, url, licenses, developers, scm);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      PomSettings other = (PomSettings) obj;
      if (!Objects.equals(this.groupId, other.groupId)) return false;
      if (!Objects.equals(this.artifactId, other.artifactId)) return false;
      if (!Objects.equals(this.version, other.version)) return false;
      if (!Objects.equals(this.name, other.name)) return false;
      if (!Objects.equals(this.description, other.description)) return false;
      if (!Objects.equals(this.url, other.url)) return false;
      if (!Objects.equals(this.licenses, other.licenses)) return false;
      if (!Objects.equals(this.developers, other.developers)) return false;
      if (!Objects.equals(this.scm, other.scm)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.groupId);
      result = 31 * result + Objects.hashCode(this.artifactId);
      result = 31 * result + Objects.hashCode(this.version);
      result = 31 * result + Objects.hashCode(this.name);
      result = 31 * result + Objects.hashCode(this.description);
      result = 31 * result + Objects.hashCode(this.url);
      result = 31 * result + Objects.hashCode(this.licenses);
      result = 31 * result + Objects.hashCode(this.developers);
      result = 31 * result + Objects.hashCode(this.scm);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(500);
      builder.append(PomSettings.class.getSimpleName()).append(" {");
      appendProperty(builder, "groupId", this.groupId);
      appendProperty(builder, "artifactId", this.artifactId);
      appendProperty(builder, "version", this.version);
      appendProperty(builder, "name", this.name);
      appendProperty(builder, "description", this.description);
      appendProperty(builder, "url", this.url);
      appendProperty(builder, "licenses", this.licenses);
      appendProperty(builder, "developers", this.developers);
      appendProperty(builder, "scm", this.scm);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ManifestSettings {
    public final @NonNull Map<@NonNull String, @NonNull String> mainAttributes;

    public final @NonNull Map<@NonNull String, @NonNull Map<@NonNull String, @NonNull String>> groups;

    public ManifestSettings(
        @Named("mainAttributes") @NonNull Map<@NonNull String, @NonNull String> mainAttributes,
        @Named("groups") @NonNull Map<@NonNull String, @NonNull Map<@NonNull String, @NonNull String>> groups) {
      this.mainAttributes = mainAttributes;
      this.groups = groups;
    }

    public ManifestSettings withMainAttributes(
        @NonNull Map<@NonNull String, @NonNull String> mainAttributes) {
      return new ManifestSettings(mainAttributes, groups);
    }

    public ManifestSettings withGroups(
        @NonNull Map<@NonNull String, @NonNull Map<@NonNull String, @NonNull String>> groups) {
      return new ManifestSettings(mainAttributes, groups);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ManifestSettings other = (ManifestSettings) obj;
      if (!Objects.equals(this.mainAttributes, other.mainAttributes)) return false;
      if (!Objects.equals(this.groups, other.groups)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.mainAttributes);
      result = 31 * result + Objects.hashCode(this.groups);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(ManifestSettings.class.getSimpleName()).append(" {");
      appendProperty(builder, "mainAttributes", this.mainAttributes);
      appendProperty(builder, "groups", this.groups);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class GraalVmSettings {
    public final String graalvmHome;

    public final @NonNull List<@NonNull String> nativeImageOptions;

    public GraalVmSettings(@Named("graalvmHome") String graalvmHome,
        @Named("nativeImageOptions") @NonNull List<@NonNull String> nativeImageOptions) {
      this.graalvmHome = graalvmHome;
      this.nativeImageOptions = nativeImageOptions;
    }

    public GraalVmSettings withGraalvmHome(String graalvmHome) {
      return new GraalVmSettings(graalvmHome, nativeImageOptions);
    }

    public GraalVmSettings withNativeImageOptions(
        @NonNull List<@NonNull String> nativeImageOptions) {
      return new GraalVmSettings(graalvmHome, nativeImageOptions);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      GraalVmSettings other = (GraalVmSettings) obj;
      if (!Objects.equals(this.graalvmHome, other.graalvmHome)) return false;
      if (!Objects.equals(this.nativeImageOptions, other.nativeImageOptions)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.graalvmHome);
      result = 31 * result + Objects.hashCode(this.nativeImageOptions);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(GraalVmSettings.class.getSimpleName()).append(" {");
      appendProperty(builder, "graalvmHome", this.graalvmHome);
      appendProperty(builder, "nativeImageOptions", this.nativeImageOptions);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class JUnitXmlReportSettings {
    public final boolean enabled;

    public final String outputDir;

    public JUnitXmlReportSettings(@Named("enabled") boolean enabled,
        @Named("outputDir") String outputDir) {
      this.enabled = enabled;
      this.outputDir = outputDir;
    }

    public JUnitXmlReportSettings withEnabled(boolean enabled) {
      return new JUnitXmlReportSettings(enabled, outputDir);
    }

    public JUnitXmlReportSettings withOutputDir(String outputDir) {
      return new JUnitXmlReportSettings(enabled, outputDir);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      JUnitXmlReportSettings other = (JUnitXmlReportSettings) obj;
      if (!Objects.equals(this.enabled, other.enabled)) return false;
      if (!Objects.equals(this.outputDir, other.outputDir)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.enabled);
      result = 31 * result + Objects.hashCode(this.outputDir);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(JUnitXmlReportSettings.class.getSimpleName()).append(" {");
      appendProperty(builder, "enabled", this.enabled);
      appendProperty(builder, "outputDir", this.outputDir);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static class MvnApp {
    public final @NonNull List<@NonNull String> deps;

    public final @NonNull String mainClass;

    public final @NonNull List<@NonNull String> args;

    public MvnApp(@Named("deps") @NonNull List<@NonNull String> deps,
        @Named("mainClass") @NonNull String mainClass,
        @Named("args") @NonNull List<@NonNull String> args) {
      this.deps = deps;
      this.mainClass = mainClass;
      this.args = args;
    }

    public MvnApp withDeps(@NonNull List<@NonNull String> deps) {
      return new MvnApp(deps, mainClass, args);
    }

    public MvnApp withMainClass(@NonNull String mainClass) {
      return new MvnApp(deps, mainClass, args);
    }

    public MvnApp withArgs(@NonNull List<@NonNull String> args) {
      return new MvnApp(deps, mainClass, args);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      MvnApp other = (MvnApp) obj;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.args, other.args)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.args);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(MvnApp.class.getSimpleName()).append(" {");
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "args", this.args);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static class ServerTool {
    public final @NonNull List<@NonNull String> deps;

    public final @NonNull String mainClass;

    public final @NonNull List<@NonNull String> args;

    public final String description;

    public ServerTool(@Named("deps") @NonNull List<@NonNull String> deps,
        @Named("mainClass") @NonNull String mainClass,
        @Named("args") @NonNull List<@NonNull String> args,
        @Named("description") String description) {
      this.deps = deps;
      this.mainClass = mainClass;
      this.args = args;
      this.description = description;
    }

    public ServerTool withDeps(@NonNull List<@NonNull String> deps) {
      return new ServerTool(deps, mainClass, args, description);
    }

    public ServerTool withMainClass(@NonNull String mainClass) {
      return new ServerTool(deps, mainClass, args, description);
    }

    public ServerTool withArgs(@NonNull List<@NonNull String> args) {
      return new ServerTool(deps, mainClass, args, description);
    }

    public ServerTool withDescription(String description) {
      return new ServerTool(deps, mainClass, args, description);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ServerTool other = (ServerTool) obj;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.args, other.args)) return false;
      if (!Objects.equals(this.description, other.description)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.args);
      result = 31 * result + Objects.hashCode(this.description);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(250);
      builder.append(ServerTool.class.getSimpleName()).append(" {");
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "args", this.args);
      appendProperty(builder, "description", this.description);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static class JavaModule extends DederModule {
    public final @NonNull List<@NonNull String> resources;

    public final String javaHome;

    public final @NonNull List<@NonNull String> jvmOptions;

    public final String javaVersion;

    public final @NonNull CompileOrder compileOrder;

    public final @NonNull List<@NonNull String> javacOptions;

    public final @NonNull Map<@NonNull String, @NonNull String> forkEnv;

    public final String mainClass;

    public final @NonNull List<@NonNull String> deps;

    public final @NonNull List<@NonNull String> compileOnlyDeps;

    public final @NonNull List<@NonNull String> javacAnnotationProcessorDeps;

    public final @NonNull String javaSemanticdbVersion;

    public final boolean semanticdbEnabled;

    public final @NonNull ManifestSettings manifest;

    public final String shadeRulesFile;

    public final boolean publish;

    public final PomSettings pomSettings;

    public final PublishRepo publishTo;

    public final String publishLocalTo;

    public final GraalVmSettings graalvm;

    public final @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps;

    public JavaModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      super(id, root, sources, moduleDeps, bspVisible, type);
      this.resources = resources;
      this.javaHome = javaHome;
      this.jvmOptions = jvmOptions;
      this.javaVersion = javaVersion;
      this.compileOrder = compileOrder;
      this.javacOptions = javacOptions;
      this.forkEnv = forkEnv;
      this.mainClass = mainClass;
      this.deps = deps;
      this.compileOnlyDeps = compileOnlyDeps;
      this.javacAnnotationProcessorDeps = javacAnnotationProcessorDeps;
      this.javaSemanticdbVersion = javaSemanticdbVersion;
      this.semanticdbEnabled = semanticdbEnabled;
      this.manifest = manifest;
      this.shadeRulesFile = shadeRulesFile;
      this.publish = publish;
      this.pomSettings = pomSettings;
      this.publishTo = publishTo;
      this.publishLocalTo = publishLocalTo;
      this.graalvm = graalvm;
      this.mvnApps = mvnApps;
    }

    public JavaModule withId(@NonNull String id) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withRoot(@NonNull String root) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withSources(@NonNull List<@NonNull String> sources) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withModuleDeps(@NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withBspVisible(Boolean bspVisible) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withType(@NonNull ModuleType type) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withResources(@NonNull List<@NonNull String> resources) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withJavaHome(String javaHome) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withJavaVersion(String javaVersion) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withMainClass(String mainClass) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withDeps(@NonNull List<@NonNull String> deps) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withManifest(@NonNull ManifestSettings manifest) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withShadeRulesFile(String shadeRulesFile) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withPublish(boolean publish) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withPomSettings(PomSettings pomSettings) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withPublishTo(PublishRepo publishTo) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withPublishLocalTo(String publishLocalTo) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withGraalvm(GraalVmSettings graalvm) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    public JavaModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new JavaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      JavaModule other = (JavaModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(1400);
      builder.append(JavaModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class JavaTestModule extends JavaModule {
    public final long testParallelism;

    public final long maxTestForks;

    public final JUnitXmlReportSettings junitXmlReport;

    public final @NonNull List<@NonNull String> testFrameworks;

    public JavaTestModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("testParallelism") long testParallelism, @Named("maxTestForks") long maxTestForks,
        @Named("junitXmlReport") JUnitXmlReportSettings junitXmlReport,
        @Named("testFrameworks") @NonNull List<@NonNull String> testFrameworks) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
      this.testParallelism = testParallelism;
      this.maxTestForks = maxTestForks;
      this.junitXmlReport = junitXmlReport;
      this.testFrameworks = testFrameworks;
    }

    public JavaTestModule withId(@NonNull String id) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withRoot(@NonNull String root) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withSources(@NonNull List<@NonNull String> sources) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withModuleDeps(@NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withBspVisible(Boolean bspVisible) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withType(@NonNull ModuleType type) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withResources(@NonNull List<@NonNull String> resources) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJavaHome(String javaHome) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJavaVersion(String javaVersion) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withMainClass(String mainClass) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withDeps(@NonNull List<@NonNull String> deps) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withManifest(@NonNull ManifestSettings manifest) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withShadeRulesFile(String shadeRulesFile) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withPublish(boolean publish) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withPomSettings(PomSettings pomSettings) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withPublishTo(PublishRepo publishTo) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withPublishLocalTo(String publishLocalTo) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withGraalvm(GraalVmSettings graalvm) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withTestParallelism(long testParallelism) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withMaxTestForks(long maxTestForks) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withJunitXmlReport(JUnitXmlReportSettings junitXmlReport) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public JavaTestModule withTestFrameworks(@NonNull List<@NonNull String> testFrameworks) {
      return new JavaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      JavaTestModule other = (JavaTestModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.testParallelism, other.testParallelism)) return false;
      if (!Objects.equals(this.maxTestForks, other.maxTestForks)) return false;
      if (!Objects.equals(this.junitXmlReport, other.junitXmlReport)) return false;
      if (!Objects.equals(this.testFrameworks, other.testFrameworks)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.testParallelism);
      result = 31 * result + Objects.hashCode(this.maxTestForks);
      result = 31 * result + Objects.hashCode(this.junitXmlReport);
      result = 31 * result + Objects.hashCode(this.testFrameworks);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(1600);
      builder.append(JavaTestModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "testParallelism", this.testParallelism);
      appendProperty(builder, "maxTestForks", this.maxTestForks);
      appendProperty(builder, "junitXmlReport", this.junitXmlReport);
      appendProperty(builder, "testFrameworks", this.testFrameworks);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static class ScalaModule extends JavaModule {
    public final @NonNull String scalaVersion;

    public final @NonNull List<@NonNull String> scalacOptions;

    public final @NonNull List<@NonNull String> scalacPluginDeps;

    public final String scalaSemanticdbVersion;

    public ScalaModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("scalaVersion") @NonNull String scalaVersion,
        @Named("scalacOptions") @NonNull List<@NonNull String> scalacOptions,
        @Named("scalacPluginDeps") @NonNull List<@NonNull String> scalacPluginDeps,
        @Named("scalaSemanticdbVersion") String scalaSemanticdbVersion) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps);
      this.scalaVersion = scalaVersion;
      this.scalacOptions = scalacOptions;
      this.scalacPluginDeps = scalacPluginDeps;
      this.scalaSemanticdbVersion = scalaSemanticdbVersion;
    }

    public ScalaModule withId(@NonNull String id) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withRoot(@NonNull String root) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withSources(@NonNull List<@NonNull String> sources) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withModuleDeps(@NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withBspVisible(Boolean bspVisible) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withType(@NonNull ModuleType type) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withResources(@NonNull List<@NonNull String> resources) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withJavaHome(String javaHome) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withJavaVersion(String javaVersion) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withMainClass(String mainClass) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withDeps(@NonNull List<@NonNull String> deps) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withManifest(@NonNull ManifestSettings manifest) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withShadeRulesFile(String shadeRulesFile) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withPublish(boolean publish) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withPomSettings(PomSettings pomSettings) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withPublishTo(PublishRepo publishTo) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withPublishLocalTo(String publishLocalTo) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withGraalvm(GraalVmSettings graalvm) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withScalaVersion(@NonNull String scalaVersion) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withScalacOptions(@NonNull List<@NonNull String> scalacOptions) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withScalacPluginDeps(@NonNull List<@NonNull String> scalacPluginDeps) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    public ScalaModule withScalaSemanticdbVersion(String scalaSemanticdbVersion) {
      return new ScalaModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaModule other = (ScalaModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.scalaVersion, other.scalaVersion)) return false;
      if (!Objects.equals(this.scalacOptions, other.scalacOptions)) return false;
      if (!Objects.equals(this.scalacPluginDeps, other.scalacPluginDeps)) return false;
      if (!Objects.equals(this.scalaSemanticdbVersion, other.scalaSemanticdbVersion)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.scalaVersion);
      result = 31 * result + Objects.hashCode(this.scalacOptions);
      result = 31 * result + Objects.hashCode(this.scalacPluginDeps);
      result = 31 * result + Objects.hashCode(this.scalaSemanticdbVersion);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(1600);
      builder.append(ScalaModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "scalaVersion", this.scalaVersion);
      appendProperty(builder, "scalacOptions", this.scalacOptions);
      appendProperty(builder, "scalacPluginDeps", this.scalacPluginDeps);
      appendProperty(builder, "scalaSemanticdbVersion", this.scalaSemanticdbVersion);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaTestModule extends ScalaModule {
    public final long testParallelism;

    public final long maxTestForks;

    public final JUnitXmlReportSettings junitXmlReport;

    public final @NonNull List<@NonNull String> testFrameworks;

    public ScalaTestModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("scalaVersion") @NonNull String scalaVersion,
        @Named("scalacOptions") @NonNull List<@NonNull String> scalacOptions,
        @Named("scalacPluginDeps") @NonNull List<@NonNull String> scalacPluginDeps,
        @Named("scalaSemanticdbVersion") String scalaSemanticdbVersion,
        @Named("testParallelism") long testParallelism, @Named("maxTestForks") long maxTestForks,
        @Named("junitXmlReport") JUnitXmlReportSettings junitXmlReport,
        @Named("testFrameworks") @NonNull List<@NonNull String> testFrameworks) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps,
          scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
      this.testParallelism = testParallelism;
      this.maxTestForks = maxTestForks;
      this.junitXmlReport = junitXmlReport;
      this.testFrameworks = testFrameworks;
    }

    public ScalaTestModule withId(@NonNull String id) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withRoot(@NonNull String root) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withSources(@NonNull List<@NonNull String> sources) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withModuleDeps(
        @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withBspVisible(Boolean bspVisible) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withType(@NonNull ModuleType type) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withResources(@NonNull List<@NonNull String> resources) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJavaHome(String javaHome) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJavaVersion(String javaVersion) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withMainClass(String mainClass) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withDeps(@NonNull List<@NonNull String> deps) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withManifest(@NonNull ManifestSettings manifest) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withShadeRulesFile(String shadeRulesFile) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withPublish(boolean publish) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withPomSettings(PomSettings pomSettings) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withPublishTo(PublishRepo publishTo) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withPublishLocalTo(String publishLocalTo) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withGraalvm(GraalVmSettings graalvm) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withScalaVersion(@NonNull String scalaVersion) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withScalacOptions(@NonNull List<@NonNull String> scalacOptions) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withScalacPluginDeps(@NonNull List<@NonNull String> scalacPluginDeps) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withScalaSemanticdbVersion(String scalaSemanticdbVersion) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withTestParallelism(long testParallelism) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withMaxTestForks(long maxTestForks) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withJunitXmlReport(JUnitXmlReportSettings junitXmlReport) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    public ScalaTestModule withTestFrameworks(@NonNull List<@NonNull String> testFrameworks) {
      return new ScalaTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, testParallelism, maxTestForks, junitXmlReport, testFrameworks);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaTestModule other = (ScalaTestModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.scalaVersion, other.scalaVersion)) return false;
      if (!Objects.equals(this.scalacOptions, other.scalacOptions)) return false;
      if (!Objects.equals(this.scalacPluginDeps, other.scalacPluginDeps)) return false;
      if (!Objects.equals(this.scalaSemanticdbVersion, other.scalaSemanticdbVersion)) return false;
      if (!Objects.equals(this.testParallelism, other.testParallelism)) return false;
      if (!Objects.equals(this.maxTestForks, other.maxTestForks)) return false;
      if (!Objects.equals(this.junitXmlReport, other.junitXmlReport)) return false;
      if (!Objects.equals(this.testFrameworks, other.testFrameworks)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.scalaVersion);
      result = 31 * result + Objects.hashCode(this.scalacOptions);
      result = 31 * result + Objects.hashCode(this.scalacPluginDeps);
      result = 31 * result + Objects.hashCode(this.scalaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.testParallelism);
      result = 31 * result + Objects.hashCode(this.maxTestForks);
      result = 31 * result + Objects.hashCode(this.junitXmlReport);
      result = 31 * result + Objects.hashCode(this.testFrameworks);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(1800);
      builder.append(ScalaTestModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "scalaVersion", this.scalaVersion);
      appendProperty(builder, "scalacOptions", this.scalacOptions);
      appendProperty(builder, "scalacPluginDeps", this.scalacPluginDeps);
      appendProperty(builder, "scalaSemanticdbVersion", this.scalaSemanticdbVersion);
      appendProperty(builder, "testParallelism", this.testParallelism);
      appendProperty(builder, "maxTestForks", this.maxTestForks);
      appendProperty(builder, "junitXmlReport", this.junitXmlReport);
      appendProperty(builder, "testFrameworks", this.testFrameworks);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaJsLinkerConfig {
    public final @NonNull ScalaJsModuleSplitStyle moduleSplitStyle;

    public final @NonNull List<@NonNull String> smallModulesFor;

    public final @NonNull ScalaJsESVersion esVersion;

    public final boolean sourceMap;

    public final boolean optimizer;

    public final boolean minify;

    public final boolean productionMode;

    public final @NonNull String jsHeader;

    public final boolean prettyPrint;

    public final @NonNull String jsOutputPattern;

    public final boolean avoidClasses;

    public final boolean avoidLetsAndConsts;

    public final boolean checkIR;

    public final String relativizeSourceMapBase;

    public final boolean batchMode;

    public final long maxConcurrentWrites;

    public final boolean allowBigIntsForLongs;

    public final boolean experimentalUseWebAssembly;

    public ScalaJsLinkerConfig(
        @Named("moduleSplitStyle") @NonNull ScalaJsModuleSplitStyle moduleSplitStyle,
        @Named("smallModulesFor") @NonNull List<@NonNull String> smallModulesFor,
        @Named("esVersion") @NonNull ScalaJsESVersion esVersion,
        @Named("sourceMap") boolean sourceMap, @Named("optimizer") boolean optimizer,
        @Named("minify") boolean minify, @Named("productionMode") boolean productionMode,
        @Named("jsHeader") @NonNull String jsHeader, @Named("prettyPrint") boolean prettyPrint,
        @Named("jsOutputPattern") @NonNull String jsOutputPattern,
        @Named("avoidClasses") boolean avoidClasses,
        @Named("avoidLetsAndConsts") boolean avoidLetsAndConsts, @Named("checkIR") boolean checkIR,
        @Named("relativizeSourceMapBase") String relativizeSourceMapBase,
        @Named("batchMode") boolean batchMode,
        @Named("maxConcurrentWrites") long maxConcurrentWrites,
        @Named("allowBigIntsForLongs") boolean allowBigIntsForLongs,
        @Named("experimentalUseWebAssembly") boolean experimentalUseWebAssembly) {
      this.moduleSplitStyle = moduleSplitStyle;
      this.smallModulesFor = smallModulesFor;
      this.esVersion = esVersion;
      this.sourceMap = sourceMap;
      this.optimizer = optimizer;
      this.minify = minify;
      this.productionMode = productionMode;
      this.jsHeader = jsHeader;
      this.prettyPrint = prettyPrint;
      this.jsOutputPattern = jsOutputPattern;
      this.avoidClasses = avoidClasses;
      this.avoidLetsAndConsts = avoidLetsAndConsts;
      this.checkIR = checkIR;
      this.relativizeSourceMapBase = relativizeSourceMapBase;
      this.batchMode = batchMode;
      this.maxConcurrentWrites = maxConcurrentWrites;
      this.allowBigIntsForLongs = allowBigIntsForLongs;
      this.experimentalUseWebAssembly = experimentalUseWebAssembly;
    }

    public ScalaJsLinkerConfig withModuleSplitStyle(
        @NonNull ScalaJsModuleSplitStyle moduleSplitStyle) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withSmallModulesFor(@NonNull List<@NonNull String> smallModulesFor) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withEsVersion(@NonNull ScalaJsESVersion esVersion) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withSourceMap(boolean sourceMap) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withOptimizer(boolean optimizer) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withMinify(boolean minify) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withProductionMode(boolean productionMode) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withJsHeader(@NonNull String jsHeader) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withPrettyPrint(boolean prettyPrint) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withJsOutputPattern(@NonNull String jsOutputPattern) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withAvoidClasses(boolean avoidClasses) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withAvoidLetsAndConsts(boolean avoidLetsAndConsts) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withCheckIR(boolean checkIR) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withRelativizeSourceMapBase(String relativizeSourceMapBase) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withBatchMode(boolean batchMode) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withMaxConcurrentWrites(long maxConcurrentWrites) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withAllowBigIntsForLongs(boolean allowBigIntsForLongs) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    public ScalaJsLinkerConfig withExperimentalUseWebAssembly(boolean experimentalUseWebAssembly) {
      return new ScalaJsLinkerConfig(moduleSplitStyle, smallModulesFor, esVersion, sourceMap, optimizer, minify, productionMode, jsHeader, prettyPrint, jsOutputPattern, avoidClasses, avoidLetsAndConsts, checkIR, relativizeSourceMapBase, batchMode, maxConcurrentWrites, allowBigIntsForLongs, experimentalUseWebAssembly);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaJsLinkerConfig other = (ScalaJsLinkerConfig) obj;
      if (!Objects.equals(this.moduleSplitStyle, other.moduleSplitStyle)) return false;
      if (!Objects.equals(this.smallModulesFor, other.smallModulesFor)) return false;
      if (!Objects.equals(this.esVersion, other.esVersion)) return false;
      if (!Objects.equals(this.sourceMap, other.sourceMap)) return false;
      if (!Objects.equals(this.optimizer, other.optimizer)) return false;
      if (!Objects.equals(this.minify, other.minify)) return false;
      if (!Objects.equals(this.productionMode, other.productionMode)) return false;
      if (!Objects.equals(this.jsHeader, other.jsHeader)) return false;
      if (!Objects.equals(this.prettyPrint, other.prettyPrint)) return false;
      if (!Objects.equals(this.jsOutputPattern, other.jsOutputPattern)) return false;
      if (!Objects.equals(this.avoidClasses, other.avoidClasses)) return false;
      if (!Objects.equals(this.avoidLetsAndConsts, other.avoidLetsAndConsts)) return false;
      if (!Objects.equals(this.checkIR, other.checkIR)) return false;
      if (!Objects.equals(this.relativizeSourceMapBase, other.relativizeSourceMapBase)) return false;
      if (!Objects.equals(this.batchMode, other.batchMode)) return false;
      if (!Objects.equals(this.maxConcurrentWrites, other.maxConcurrentWrites)) return false;
      if (!Objects.equals(this.allowBigIntsForLongs, other.allowBigIntsForLongs)) return false;
      if (!Objects.equals(this.experimentalUseWebAssembly, other.experimentalUseWebAssembly)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.moduleSplitStyle);
      result = 31 * result + Objects.hashCode(this.smallModulesFor);
      result = 31 * result + Objects.hashCode(this.esVersion);
      result = 31 * result + Objects.hashCode(this.sourceMap);
      result = 31 * result + Objects.hashCode(this.optimizer);
      result = 31 * result + Objects.hashCode(this.minify);
      result = 31 * result + Objects.hashCode(this.productionMode);
      result = 31 * result + Objects.hashCode(this.jsHeader);
      result = 31 * result + Objects.hashCode(this.prettyPrint);
      result = 31 * result + Objects.hashCode(this.jsOutputPattern);
      result = 31 * result + Objects.hashCode(this.avoidClasses);
      result = 31 * result + Objects.hashCode(this.avoidLetsAndConsts);
      result = 31 * result + Objects.hashCode(this.checkIR);
      result = 31 * result + Objects.hashCode(this.relativizeSourceMapBase);
      result = 31 * result + Objects.hashCode(this.batchMode);
      result = 31 * result + Objects.hashCode(this.maxConcurrentWrites);
      result = 31 * result + Objects.hashCode(this.allowBigIntsForLongs);
      result = 31 * result + Objects.hashCode(this.experimentalUseWebAssembly);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(950);
      builder.append(ScalaJsLinkerConfig.class.getSimpleName()).append(" {");
      appendProperty(builder, "moduleSplitStyle", this.moduleSplitStyle);
      appendProperty(builder, "smallModulesFor", this.smallModulesFor);
      appendProperty(builder, "esVersion", this.esVersion);
      appendProperty(builder, "sourceMap", this.sourceMap);
      appendProperty(builder, "optimizer", this.optimizer);
      appendProperty(builder, "minify", this.minify);
      appendProperty(builder, "productionMode", this.productionMode);
      appendProperty(builder, "jsHeader", this.jsHeader);
      appendProperty(builder, "prettyPrint", this.prettyPrint);
      appendProperty(builder, "jsOutputPattern", this.jsOutputPattern);
      appendProperty(builder, "avoidClasses", this.avoidClasses);
      appendProperty(builder, "avoidLetsAndConsts", this.avoidLetsAndConsts);
      appendProperty(builder, "checkIR", this.checkIR);
      appendProperty(builder, "relativizeSourceMapBase", this.relativizeSourceMapBase);
      appendProperty(builder, "batchMode", this.batchMode);
      appendProperty(builder, "maxConcurrentWrites", this.maxConcurrentWrites);
      appendProperty(builder, "allowBigIntsForLongs", this.allowBigIntsForLongs);
      appendProperty(builder, "experimentalUseWebAssembly", this.experimentalUseWebAssembly);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static class ScalaJsModule extends ScalaModule {
    public final @NonNull String scalaJsVersion;

    public final @NonNull ScalaJsModuleKind moduleKind;

    public final @NonNull ScalaJsLinkerConfig linkerConfig;

    public ScalaJsModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("scalaVersion") @NonNull String scalaVersion,
        @Named("scalacOptions") @NonNull List<@NonNull String> scalacOptions,
        @Named("scalacPluginDeps") @NonNull List<@NonNull String> scalacPluginDeps,
        @Named("scalaSemanticdbVersion") String scalaSemanticdbVersion,
        @Named("scalaJsVersion") @NonNull String scalaJsVersion,
        @Named("moduleKind") @NonNull ScalaJsModuleKind moduleKind,
        @Named("linkerConfig") @NonNull ScalaJsLinkerConfig linkerConfig) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps,
          scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
      this.scalaJsVersion = scalaJsVersion;
      this.moduleKind = moduleKind;
      this.linkerConfig = linkerConfig;
    }

    public ScalaJsModule withId(@NonNull String id) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withRoot(@NonNull String root) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withSources(@NonNull List<@NonNull String> sources) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withModuleDeps(@NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withBspVisible(Boolean bspVisible) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withType(@NonNull ModuleType type) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withResources(@NonNull List<@NonNull String> resources) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withJavaHome(String javaHome) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withJavaVersion(String javaVersion) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withMainClass(String mainClass) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withDeps(@NonNull List<@NonNull String> deps) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withManifest(@NonNull ManifestSettings manifest) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withShadeRulesFile(String shadeRulesFile) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withPublish(boolean publish) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withPomSettings(PomSettings pomSettings) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withPublishTo(PublishRepo publishTo) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withPublishLocalTo(String publishLocalTo) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withGraalvm(GraalVmSettings graalvm) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withScalaVersion(@NonNull String scalaVersion) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withScalacOptions(@NonNull List<@NonNull String> scalacOptions) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withScalacPluginDeps(@NonNull List<@NonNull String> scalacPluginDeps) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withScalaSemanticdbVersion(String scalaSemanticdbVersion) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withScalaJsVersion(@NonNull String scalaJsVersion) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withModuleKind(@NonNull ScalaJsModuleKind moduleKind) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    public ScalaJsModule withLinkerConfig(@NonNull ScalaJsLinkerConfig linkerConfig) {
      return new ScalaJsModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaJsModule other = (ScalaJsModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.scalaVersion, other.scalaVersion)) return false;
      if (!Objects.equals(this.scalacOptions, other.scalacOptions)) return false;
      if (!Objects.equals(this.scalacPluginDeps, other.scalacPluginDeps)) return false;
      if (!Objects.equals(this.scalaSemanticdbVersion, other.scalaSemanticdbVersion)) return false;
      if (!Objects.equals(this.scalaJsVersion, other.scalaJsVersion)) return false;
      if (!Objects.equals(this.moduleKind, other.moduleKind)) return false;
      if (!Objects.equals(this.linkerConfig, other.linkerConfig)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.scalaVersion);
      result = 31 * result + Objects.hashCode(this.scalacOptions);
      result = 31 * result + Objects.hashCode(this.scalacPluginDeps);
      result = 31 * result + Objects.hashCode(this.scalaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.scalaJsVersion);
      result = 31 * result + Objects.hashCode(this.moduleKind);
      result = 31 * result + Objects.hashCode(this.linkerConfig);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(1750);
      builder.append(ScalaJsModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "scalaVersion", this.scalaVersion);
      appendProperty(builder, "scalacOptions", this.scalacOptions);
      appendProperty(builder, "scalacPluginDeps", this.scalacPluginDeps);
      appendProperty(builder, "scalaSemanticdbVersion", this.scalaSemanticdbVersion);
      appendProperty(builder, "scalaJsVersion", this.scalaJsVersion);
      appendProperty(builder, "moduleKind", this.moduleKind);
      appendProperty(builder, "linkerConfig", this.linkerConfig);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaJsTestModule extends ScalaJsModule {
    public final long testParallelism;

    public final JUnitXmlReportSettings junitXmlReport;

    public final @NonNull List<@NonNull String> testFrameworks;

    public ScalaJsTestModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("scalaVersion") @NonNull String scalaVersion,
        @Named("scalacOptions") @NonNull List<@NonNull String> scalacOptions,
        @Named("scalacPluginDeps") @NonNull List<@NonNull String> scalacPluginDeps,
        @Named("scalaSemanticdbVersion") String scalaSemanticdbVersion,
        @Named("scalaJsVersion") @NonNull String scalaJsVersion,
        @Named("moduleKind") @NonNull ScalaJsModuleKind moduleKind,
        @Named("linkerConfig") @NonNull ScalaJsLinkerConfig linkerConfig,
        @Named("testParallelism") long testParallelism,
        @Named("junitXmlReport") JUnitXmlReportSettings junitXmlReport,
        @Named("testFrameworks") @NonNull List<@NonNull String> testFrameworks) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps,
          scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion,
          moduleKind, linkerConfig);
      this.testParallelism = testParallelism;
      this.junitXmlReport = junitXmlReport;
      this.testFrameworks = testFrameworks;
    }

    public ScalaJsTestModule withId(@NonNull String id) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withRoot(@NonNull String root) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withSources(@NonNull List<@NonNull String> sources) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withModuleDeps(
        @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withBspVisible(Boolean bspVisible) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withType(@NonNull ModuleType type) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withResources(@NonNull List<@NonNull String> resources) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJavaHome(String javaHome) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJavaVersion(String javaVersion) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withMainClass(String mainClass) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withDeps(@NonNull List<@NonNull String> deps) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withManifest(@NonNull ManifestSettings manifest) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withShadeRulesFile(String shadeRulesFile) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withPublish(boolean publish) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withPomSettings(PomSettings pomSettings) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withPublishTo(PublishRepo publishTo) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withPublishLocalTo(String publishLocalTo) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withGraalvm(GraalVmSettings graalvm) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withScalaVersion(@NonNull String scalaVersion) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withScalacOptions(@NonNull List<@NonNull String> scalacOptions) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withScalacPluginDeps(@NonNull List<@NonNull String> scalacPluginDeps) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withScalaSemanticdbVersion(String scalaSemanticdbVersion) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withScalaJsVersion(@NonNull String scalaJsVersion) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withModuleKind(@NonNull ScalaJsModuleKind moduleKind) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withLinkerConfig(@NonNull ScalaJsLinkerConfig linkerConfig) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withTestParallelism(long testParallelism) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withJunitXmlReport(JUnitXmlReportSettings junitXmlReport) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaJsTestModule withTestFrameworks(@NonNull List<@NonNull String> testFrameworks) {
      return new ScalaJsTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaJsVersion, moduleKind, linkerConfig, testParallelism, junitXmlReport, testFrameworks);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaJsTestModule other = (ScalaJsTestModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.scalaVersion, other.scalaVersion)) return false;
      if (!Objects.equals(this.scalacOptions, other.scalacOptions)) return false;
      if (!Objects.equals(this.scalacPluginDeps, other.scalacPluginDeps)) return false;
      if (!Objects.equals(this.scalaSemanticdbVersion, other.scalaSemanticdbVersion)) return false;
      if (!Objects.equals(this.scalaJsVersion, other.scalaJsVersion)) return false;
      if (!Objects.equals(this.moduleKind, other.moduleKind)) return false;
      if (!Objects.equals(this.linkerConfig, other.linkerConfig)) return false;
      if (!Objects.equals(this.testParallelism, other.testParallelism)) return false;
      if (!Objects.equals(this.junitXmlReport, other.junitXmlReport)) return false;
      if (!Objects.equals(this.testFrameworks, other.testFrameworks)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.scalaVersion);
      result = 31 * result + Objects.hashCode(this.scalacOptions);
      result = 31 * result + Objects.hashCode(this.scalacPluginDeps);
      result = 31 * result + Objects.hashCode(this.scalaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.scalaJsVersion);
      result = 31 * result + Objects.hashCode(this.moduleKind);
      result = 31 * result + Objects.hashCode(this.linkerConfig);
      result = 31 * result + Objects.hashCode(this.testParallelism);
      result = 31 * result + Objects.hashCode(this.junitXmlReport);
      result = 31 * result + Objects.hashCode(this.testFrameworks);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(1900);
      builder.append(ScalaJsTestModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "scalaVersion", this.scalaVersion);
      appendProperty(builder, "scalacOptions", this.scalacOptions);
      appendProperty(builder, "scalacPluginDeps", this.scalacPluginDeps);
      appendProperty(builder, "scalaSemanticdbVersion", this.scalaSemanticdbVersion);
      appendProperty(builder, "scalaJsVersion", this.scalaJsVersion);
      appendProperty(builder, "moduleKind", this.moduleKind);
      appendProperty(builder, "linkerConfig", this.linkerConfig);
      appendProperty(builder, "testParallelism", this.testParallelism);
      appendProperty(builder, "junitXmlReport", this.junitXmlReport);
      appendProperty(builder, "testFrameworks", this.testFrameworks);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static class ScalaNativeModule extends ScalaModule {
    public final @NonNull String scalaNativeVersion;

    public final @NonNull ScalaNativeGC gc;

    public final @NonNull ScalaNativeMode mode;

    public final boolean multithreading;

    public final @NonNull ScalaNativeLTO lto;

    public final boolean embedResources;

    public final boolean linkStubs;

    public final boolean check;

    public final boolean checkFatalWarnings;

    public final boolean optimize;

    public final String targetTriple;

    public final @NonNull List<@NonNull String> nativeLinkingOptions;

    public final @NonNull List<@NonNull String> nativeCompileOptions;

    public final @NonNull List<@NonNull String> nativeCOptions;

    public final @NonNull List<@NonNull String> nativeCppOptions;

    public final @NonNull List<@NonNull String> resourceIncludePatterns;

    public final @NonNull List<@NonNull String> resourceExcludePatterns;

    public ScalaNativeModule(@Named("id") @NonNull String id, @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("scalaVersion") @NonNull String scalaVersion,
        @Named("scalacOptions") @NonNull List<@NonNull String> scalacOptions,
        @Named("scalacPluginDeps") @NonNull List<@NonNull String> scalacPluginDeps,
        @Named("scalaSemanticdbVersion") String scalaSemanticdbVersion,
        @Named("scalaNativeVersion") @NonNull String scalaNativeVersion,
        @Named("gc") @NonNull ScalaNativeGC gc, @Named("mode") @NonNull ScalaNativeMode mode,
        @Named("multithreading") boolean multithreading, @Named("lto") @NonNull ScalaNativeLTO lto,
        @Named("embedResources") boolean embedResources, @Named("linkStubs") boolean linkStubs,
        @Named("check") boolean check, @Named("checkFatalWarnings") boolean checkFatalWarnings,
        @Named("optimize") boolean optimize, @Named("targetTriple") String targetTriple,
        @Named("nativeLinkingOptions") @NonNull List<@NonNull String> nativeLinkingOptions,
        @Named("nativeCompileOptions") @NonNull List<@NonNull String> nativeCompileOptions,
        @Named("nativeCOptions") @NonNull List<@NonNull String> nativeCOptions,
        @Named("nativeCppOptions") @NonNull List<@NonNull String> nativeCppOptions,
        @Named("resourceIncludePatterns") @NonNull List<@NonNull String> resourceIncludePatterns,
        @Named("resourceExcludePatterns") @NonNull List<@NonNull String> resourceExcludePatterns) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps,
          scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion);
      this.scalaNativeVersion = scalaNativeVersion;
      this.gc = gc;
      this.mode = mode;
      this.multithreading = multithreading;
      this.lto = lto;
      this.embedResources = embedResources;
      this.linkStubs = linkStubs;
      this.check = check;
      this.checkFatalWarnings = checkFatalWarnings;
      this.optimize = optimize;
      this.targetTriple = targetTriple;
      this.nativeLinkingOptions = nativeLinkingOptions;
      this.nativeCompileOptions = nativeCompileOptions;
      this.nativeCOptions = nativeCOptions;
      this.nativeCppOptions = nativeCppOptions;
      this.resourceIncludePatterns = resourceIncludePatterns;
      this.resourceExcludePatterns = resourceExcludePatterns;
    }

    public ScalaNativeModule withId(@NonNull String id) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withRoot(@NonNull String root) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withSources(@NonNull List<@NonNull String> sources) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withModuleDeps(
        @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withBspVisible(Boolean bspVisible) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withType(@NonNull ModuleType type) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withResources(@NonNull List<@NonNull String> resources) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withJavaHome(String javaHome) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withJavaVersion(String javaVersion) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withForkEnv(@NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withMainClass(String mainClass) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withDeps(@NonNull List<@NonNull String> deps) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withCompileOnlyDeps(@NonNull List<@NonNull String> compileOnlyDeps) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withManifest(@NonNull ManifestSettings manifest) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withShadeRulesFile(String shadeRulesFile) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withPublish(boolean publish) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withPomSettings(PomSettings pomSettings) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withPublishTo(PublishRepo publishTo) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withPublishLocalTo(String publishLocalTo) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withGraalvm(GraalVmSettings graalvm) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withScalaVersion(@NonNull String scalaVersion) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withScalacOptions(@NonNull List<@NonNull String> scalacOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withScalacPluginDeps(@NonNull List<@NonNull String> scalacPluginDeps) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withScalaSemanticdbVersion(String scalaSemanticdbVersion) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withScalaNativeVersion(@NonNull String scalaNativeVersion) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withGc(@NonNull ScalaNativeGC gc) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withMode(@NonNull ScalaNativeMode mode) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withMultithreading(boolean multithreading) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withLto(@NonNull ScalaNativeLTO lto) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withEmbedResources(boolean embedResources) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withLinkStubs(boolean linkStubs) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withCheck(boolean check) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withCheckFatalWarnings(boolean checkFatalWarnings) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withOptimize(boolean optimize) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withTargetTriple(String targetTriple) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withNativeLinkingOptions(
        @NonNull List<@NonNull String> nativeLinkingOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withNativeCompileOptions(
        @NonNull List<@NonNull String> nativeCompileOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withNativeCOptions(@NonNull List<@NonNull String> nativeCOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withNativeCppOptions(@NonNull List<@NonNull String> nativeCppOptions) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withResourceIncludePatterns(
        @NonNull List<@NonNull String> resourceIncludePatterns) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    public ScalaNativeModule withResourceExcludePatterns(
        @NonNull List<@NonNull String> resourceExcludePatterns) {
      return new ScalaNativeModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaNativeModule other = (ScalaNativeModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.scalaVersion, other.scalaVersion)) return false;
      if (!Objects.equals(this.scalacOptions, other.scalacOptions)) return false;
      if (!Objects.equals(this.scalacPluginDeps, other.scalacPluginDeps)) return false;
      if (!Objects.equals(this.scalaSemanticdbVersion, other.scalaSemanticdbVersion)) return false;
      if (!Objects.equals(this.scalaNativeVersion, other.scalaNativeVersion)) return false;
      if (!Objects.equals(this.gc, other.gc)) return false;
      if (!Objects.equals(this.mode, other.mode)) return false;
      if (!Objects.equals(this.multithreading, other.multithreading)) return false;
      if (!Objects.equals(this.lto, other.lto)) return false;
      if (!Objects.equals(this.embedResources, other.embedResources)) return false;
      if (!Objects.equals(this.linkStubs, other.linkStubs)) return false;
      if (!Objects.equals(this.check, other.check)) return false;
      if (!Objects.equals(this.checkFatalWarnings, other.checkFatalWarnings)) return false;
      if (!Objects.equals(this.optimize, other.optimize)) return false;
      if (!Objects.equals(this.targetTriple, other.targetTriple)) return false;
      if (!Objects.equals(this.nativeLinkingOptions, other.nativeLinkingOptions)) return false;
      if (!Objects.equals(this.nativeCompileOptions, other.nativeCompileOptions)) return false;
      if (!Objects.equals(this.nativeCOptions, other.nativeCOptions)) return false;
      if (!Objects.equals(this.nativeCppOptions, other.nativeCppOptions)) return false;
      if (!Objects.equals(this.resourceIncludePatterns, other.resourceIncludePatterns)) return false;
      if (!Objects.equals(this.resourceExcludePatterns, other.resourceExcludePatterns)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.scalaVersion);
      result = 31 * result + Objects.hashCode(this.scalacOptions);
      result = 31 * result + Objects.hashCode(this.scalacPluginDeps);
      result = 31 * result + Objects.hashCode(this.scalaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.scalaNativeVersion);
      result = 31 * result + Objects.hashCode(this.gc);
      result = 31 * result + Objects.hashCode(this.mode);
      result = 31 * result + Objects.hashCode(this.multithreading);
      result = 31 * result + Objects.hashCode(this.lto);
      result = 31 * result + Objects.hashCode(this.embedResources);
      result = 31 * result + Objects.hashCode(this.linkStubs);
      result = 31 * result + Objects.hashCode(this.check);
      result = 31 * result + Objects.hashCode(this.checkFatalWarnings);
      result = 31 * result + Objects.hashCode(this.optimize);
      result = 31 * result + Objects.hashCode(this.targetTriple);
      result = 31 * result + Objects.hashCode(this.nativeLinkingOptions);
      result = 31 * result + Objects.hashCode(this.nativeCompileOptions);
      result = 31 * result + Objects.hashCode(this.nativeCOptions);
      result = 31 * result + Objects.hashCode(this.nativeCppOptions);
      result = 31 * result + Objects.hashCode(this.resourceIncludePatterns);
      result = 31 * result + Objects.hashCode(this.resourceExcludePatterns);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(2450);
      builder.append(ScalaNativeModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "scalaVersion", this.scalaVersion);
      appendProperty(builder, "scalacOptions", this.scalacOptions);
      appendProperty(builder, "scalacPluginDeps", this.scalacPluginDeps);
      appendProperty(builder, "scalaSemanticdbVersion", this.scalaSemanticdbVersion);
      appendProperty(builder, "scalaNativeVersion", this.scalaNativeVersion);
      appendProperty(builder, "gc", this.gc);
      appendProperty(builder, "mode", this.mode);
      appendProperty(builder, "multithreading", this.multithreading);
      appendProperty(builder, "lto", this.lto);
      appendProperty(builder, "embedResources", this.embedResources);
      appendProperty(builder, "linkStubs", this.linkStubs);
      appendProperty(builder, "check", this.check);
      appendProperty(builder, "checkFatalWarnings", this.checkFatalWarnings);
      appendProperty(builder, "optimize", this.optimize);
      appendProperty(builder, "targetTriple", this.targetTriple);
      appendProperty(builder, "nativeLinkingOptions", this.nativeLinkingOptions);
      appendProperty(builder, "nativeCompileOptions", this.nativeCompileOptions);
      appendProperty(builder, "nativeCOptions", this.nativeCOptions);
      appendProperty(builder, "nativeCppOptions", this.nativeCppOptions);
      appendProperty(builder, "resourceIncludePatterns", this.resourceIncludePatterns);
      appendProperty(builder, "resourceExcludePatterns", this.resourceExcludePatterns);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaNativeTestModule extends ScalaNativeModule {
    public final long testParallelism;

    public final JUnitXmlReportSettings junitXmlReport;

    public final @NonNull List<@NonNull String> testFrameworks;

    public ScalaNativeTestModule(@Named("id") @NonNull String id,
        @Named("root") @NonNull String root,
        @Named("sources") @NonNull List<@NonNull String> sources,
        @Named("moduleDeps") @NonNull List<? extends @NonNull DederModule> moduleDeps,
        @Named("bspVisible") Boolean bspVisible, @Named("type") @NonNull ModuleType type,
        @Named("resources") @NonNull List<@NonNull String> resources,
        @Named("javaHome") String javaHome,
        @Named("jvmOptions") @NonNull List<@NonNull String> jvmOptions,
        @Named("javaVersion") String javaVersion,
        @Named("compileOrder") @NonNull CompileOrder compileOrder,
        @Named("javacOptions") @NonNull List<@NonNull String> javacOptions,
        @Named("forkEnv") @NonNull Map<@NonNull String, @NonNull String> forkEnv,
        @Named("mainClass") String mainClass, @Named("deps") @NonNull List<@NonNull String> deps,
        @Named("compileOnlyDeps") @NonNull List<@NonNull String> compileOnlyDeps,
        @Named("javacAnnotationProcessorDeps") @NonNull List<@NonNull String> javacAnnotationProcessorDeps,
        @Named("javaSemanticdbVersion") @NonNull String javaSemanticdbVersion,
        @Named("semanticdbEnabled") boolean semanticdbEnabled,
        @Named("manifest") @NonNull ManifestSettings manifest,
        @Named("shadeRulesFile") String shadeRulesFile, @Named("publish") boolean publish,
        @Named("pomSettings") PomSettings pomSettings, @Named("publishTo") PublishRepo publishTo,
        @Named("publishLocalTo") String publishLocalTo, @Named("graalvm") GraalVmSettings graalvm,
        @Named("mvnApps") @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps,
        @Named("scalaVersion") @NonNull String scalaVersion,
        @Named("scalacOptions") @NonNull List<@NonNull String> scalacOptions,
        @Named("scalacPluginDeps") @NonNull List<@NonNull String> scalacPluginDeps,
        @Named("scalaSemanticdbVersion") String scalaSemanticdbVersion,
        @Named("scalaNativeVersion") @NonNull String scalaNativeVersion,
        @Named("gc") @NonNull ScalaNativeGC gc, @Named("mode") @NonNull ScalaNativeMode mode,
        @Named("multithreading") boolean multithreading, @Named("lto") @NonNull ScalaNativeLTO lto,
        @Named("embedResources") boolean embedResources, @Named("linkStubs") boolean linkStubs,
        @Named("check") boolean check, @Named("checkFatalWarnings") boolean checkFatalWarnings,
        @Named("optimize") boolean optimize, @Named("targetTriple") String targetTriple,
        @Named("nativeLinkingOptions") @NonNull List<@NonNull String> nativeLinkingOptions,
        @Named("nativeCompileOptions") @NonNull List<@NonNull String> nativeCompileOptions,
        @Named("nativeCOptions") @NonNull List<@NonNull String> nativeCOptions,
        @Named("nativeCppOptions") @NonNull List<@NonNull String> nativeCppOptions,
        @Named("resourceIncludePatterns") @NonNull List<@NonNull String> resourceIncludePatterns,
        @Named("resourceExcludePatterns") @NonNull List<@NonNull String> resourceExcludePatterns,
        @Named("testParallelism") long testParallelism,
        @Named("junitXmlReport") JUnitXmlReportSettings junitXmlReport,
        @Named("testFrameworks") @NonNull List<@NonNull String> testFrameworks) {
      super(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions,
          javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps,
          javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest,
          shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps,
          scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion,
          gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings,
          optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions,
          nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns);
      this.testParallelism = testParallelism;
      this.junitXmlReport = junitXmlReport;
      this.testFrameworks = testFrameworks;
    }

    public ScalaNativeTestModule withId(@NonNull String id) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withRoot(@NonNull String root) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withSources(@NonNull List<@NonNull String> sources) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withModuleDeps(
        @NonNull List<? extends @NonNull DederModule> moduleDeps) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withBspVisible(Boolean bspVisible) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withType(@NonNull ModuleType type) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withResources(@NonNull List<@NonNull String> resources) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJavaHome(String javaHome) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJvmOptions(@NonNull List<@NonNull String> jvmOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJavaVersion(String javaVersion) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withCompileOrder(@NonNull CompileOrder compileOrder) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJavacOptions(@NonNull List<@NonNull String> javacOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withForkEnv(
        @NonNull Map<@NonNull String, @NonNull String> forkEnv) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withMainClass(String mainClass) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withDeps(@NonNull List<@NonNull String> deps) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withCompileOnlyDeps(
        @NonNull List<@NonNull String> compileOnlyDeps) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJavacAnnotationProcessorDeps(
        @NonNull List<@NonNull String> javacAnnotationProcessorDeps) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJavaSemanticdbVersion(@NonNull String javaSemanticdbVersion) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withSemanticdbEnabled(boolean semanticdbEnabled) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withManifest(@NonNull ManifestSettings manifest) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withShadeRulesFile(String shadeRulesFile) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withPublish(boolean publish) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withPomSettings(PomSettings pomSettings) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withPublishTo(PublishRepo publishTo) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withPublishLocalTo(String publishLocalTo) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withGraalvm(GraalVmSettings graalvm) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withMvnApps(
        @NonNull Map<@NonNull String, ? extends @NonNull MvnApp> mvnApps) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withScalaVersion(@NonNull String scalaVersion) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withScalacOptions(@NonNull List<@NonNull String> scalacOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withScalacPluginDeps(
        @NonNull List<@NonNull String> scalacPluginDeps) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withScalaSemanticdbVersion(String scalaSemanticdbVersion) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withScalaNativeVersion(@NonNull String scalaNativeVersion) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withGc(@NonNull ScalaNativeGC gc) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withMode(@NonNull ScalaNativeMode mode) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withMultithreading(boolean multithreading) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withLto(@NonNull ScalaNativeLTO lto) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withEmbedResources(boolean embedResources) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withLinkStubs(boolean linkStubs) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withCheck(boolean check) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withCheckFatalWarnings(boolean checkFatalWarnings) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withOptimize(boolean optimize) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withTargetTriple(String targetTriple) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withNativeLinkingOptions(
        @NonNull List<@NonNull String> nativeLinkingOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withNativeCompileOptions(
        @NonNull List<@NonNull String> nativeCompileOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withNativeCOptions(@NonNull List<@NonNull String> nativeCOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withNativeCppOptions(
        @NonNull List<@NonNull String> nativeCppOptions) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withResourceIncludePatterns(
        @NonNull List<@NonNull String> resourceIncludePatterns) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withResourceExcludePatterns(
        @NonNull List<@NonNull String> resourceExcludePatterns) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withTestParallelism(long testParallelism) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withJunitXmlReport(JUnitXmlReportSettings junitXmlReport) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    public ScalaNativeTestModule withTestFrameworks(@NonNull List<@NonNull String> testFrameworks) {
      return new ScalaNativeTestModule(id, root, sources, moduleDeps, bspVisible, type, resources, javaHome, jvmOptions, javaVersion, compileOrder, javacOptions, forkEnv, mainClass, deps, compileOnlyDeps, javacAnnotationProcessorDeps, javaSemanticdbVersion, semanticdbEnabled, manifest, shadeRulesFile, publish, pomSettings, publishTo, publishLocalTo, graalvm, mvnApps, scalaVersion, scalacOptions, scalacPluginDeps, scalaSemanticdbVersion, scalaNativeVersion, gc, mode, multithreading, lto, embedResources, linkStubs, check, checkFatalWarnings, optimize, targetTriple, nativeLinkingOptions, nativeCompileOptions, nativeCOptions, nativeCppOptions, resourceIncludePatterns, resourceExcludePatterns, testParallelism, junitXmlReport, testFrameworks);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaNativeTestModule other = (ScalaNativeTestModule) obj;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.sources, other.sources)) return false;
      if (!Objects.equals(this.moduleDeps, other.moduleDeps)) return false;
      if (!Objects.equals(this.bspVisible, other.bspVisible)) return false;
      if (!Objects.equals(this.type, other.type)) return false;
      if (!Objects.equals(this.resources, other.resources)) return false;
      if (!Objects.equals(this.javaHome, other.javaHome)) return false;
      if (!Objects.equals(this.jvmOptions, other.jvmOptions)) return false;
      if (!Objects.equals(this.javaVersion, other.javaVersion)) return false;
      if (!Objects.equals(this.compileOrder, other.compileOrder)) return false;
      if (!Objects.equals(this.javacOptions, other.javacOptions)) return false;
      if (!Objects.equals(this.forkEnv, other.forkEnv)) return false;
      if (!Objects.equals(this.mainClass, other.mainClass)) return false;
      if (!Objects.equals(this.deps, other.deps)) return false;
      if (!Objects.equals(this.compileOnlyDeps, other.compileOnlyDeps)) return false;
      if (!Objects.equals(this.javacAnnotationProcessorDeps, other.javacAnnotationProcessorDeps)) return false;
      if (!Objects.equals(this.javaSemanticdbVersion, other.javaSemanticdbVersion)) return false;
      if (!Objects.equals(this.semanticdbEnabled, other.semanticdbEnabled)) return false;
      if (!Objects.equals(this.manifest, other.manifest)) return false;
      if (!Objects.equals(this.shadeRulesFile, other.shadeRulesFile)) return false;
      if (!Objects.equals(this.publish, other.publish)) return false;
      if (!Objects.equals(this.pomSettings, other.pomSettings)) return false;
      if (!Objects.equals(this.publishTo, other.publishTo)) return false;
      if (!Objects.equals(this.publishLocalTo, other.publishLocalTo)) return false;
      if (!Objects.equals(this.graalvm, other.graalvm)) return false;
      if (!Objects.equals(this.mvnApps, other.mvnApps)) return false;
      if (!Objects.equals(this.scalaVersion, other.scalaVersion)) return false;
      if (!Objects.equals(this.scalacOptions, other.scalacOptions)) return false;
      if (!Objects.equals(this.scalacPluginDeps, other.scalacPluginDeps)) return false;
      if (!Objects.equals(this.scalaSemanticdbVersion, other.scalaSemanticdbVersion)) return false;
      if (!Objects.equals(this.scalaNativeVersion, other.scalaNativeVersion)) return false;
      if (!Objects.equals(this.gc, other.gc)) return false;
      if (!Objects.equals(this.mode, other.mode)) return false;
      if (!Objects.equals(this.multithreading, other.multithreading)) return false;
      if (!Objects.equals(this.lto, other.lto)) return false;
      if (!Objects.equals(this.embedResources, other.embedResources)) return false;
      if (!Objects.equals(this.linkStubs, other.linkStubs)) return false;
      if (!Objects.equals(this.check, other.check)) return false;
      if (!Objects.equals(this.checkFatalWarnings, other.checkFatalWarnings)) return false;
      if (!Objects.equals(this.optimize, other.optimize)) return false;
      if (!Objects.equals(this.targetTriple, other.targetTriple)) return false;
      if (!Objects.equals(this.nativeLinkingOptions, other.nativeLinkingOptions)) return false;
      if (!Objects.equals(this.nativeCompileOptions, other.nativeCompileOptions)) return false;
      if (!Objects.equals(this.nativeCOptions, other.nativeCOptions)) return false;
      if (!Objects.equals(this.nativeCppOptions, other.nativeCppOptions)) return false;
      if (!Objects.equals(this.resourceIncludePatterns, other.resourceIncludePatterns)) return false;
      if (!Objects.equals(this.resourceExcludePatterns, other.resourceExcludePatterns)) return false;
      if (!Objects.equals(this.testParallelism, other.testParallelism)) return false;
      if (!Objects.equals(this.junitXmlReport, other.junitXmlReport)) return false;
      if (!Objects.equals(this.testFrameworks, other.testFrameworks)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.sources);
      result = 31 * result + Objects.hashCode(this.moduleDeps);
      result = 31 * result + Objects.hashCode(this.bspVisible);
      result = 31 * result + Objects.hashCode(this.type);
      result = 31 * result + Objects.hashCode(this.resources);
      result = 31 * result + Objects.hashCode(this.javaHome);
      result = 31 * result + Objects.hashCode(this.jvmOptions);
      result = 31 * result + Objects.hashCode(this.javaVersion);
      result = 31 * result + Objects.hashCode(this.compileOrder);
      result = 31 * result + Objects.hashCode(this.javacOptions);
      result = 31 * result + Objects.hashCode(this.forkEnv);
      result = 31 * result + Objects.hashCode(this.mainClass);
      result = 31 * result + Objects.hashCode(this.deps);
      result = 31 * result + Objects.hashCode(this.compileOnlyDeps);
      result = 31 * result + Objects.hashCode(this.javacAnnotationProcessorDeps);
      result = 31 * result + Objects.hashCode(this.javaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.semanticdbEnabled);
      result = 31 * result + Objects.hashCode(this.manifest);
      result = 31 * result + Objects.hashCode(this.shadeRulesFile);
      result = 31 * result + Objects.hashCode(this.publish);
      result = 31 * result + Objects.hashCode(this.pomSettings);
      result = 31 * result + Objects.hashCode(this.publishTo);
      result = 31 * result + Objects.hashCode(this.publishLocalTo);
      result = 31 * result + Objects.hashCode(this.graalvm);
      result = 31 * result + Objects.hashCode(this.mvnApps);
      result = 31 * result + Objects.hashCode(this.scalaVersion);
      result = 31 * result + Objects.hashCode(this.scalacOptions);
      result = 31 * result + Objects.hashCode(this.scalacPluginDeps);
      result = 31 * result + Objects.hashCode(this.scalaSemanticdbVersion);
      result = 31 * result + Objects.hashCode(this.scalaNativeVersion);
      result = 31 * result + Objects.hashCode(this.gc);
      result = 31 * result + Objects.hashCode(this.mode);
      result = 31 * result + Objects.hashCode(this.multithreading);
      result = 31 * result + Objects.hashCode(this.lto);
      result = 31 * result + Objects.hashCode(this.embedResources);
      result = 31 * result + Objects.hashCode(this.linkStubs);
      result = 31 * result + Objects.hashCode(this.check);
      result = 31 * result + Objects.hashCode(this.checkFatalWarnings);
      result = 31 * result + Objects.hashCode(this.optimize);
      result = 31 * result + Objects.hashCode(this.targetTriple);
      result = 31 * result + Objects.hashCode(this.nativeLinkingOptions);
      result = 31 * result + Objects.hashCode(this.nativeCompileOptions);
      result = 31 * result + Objects.hashCode(this.nativeCOptions);
      result = 31 * result + Objects.hashCode(this.nativeCppOptions);
      result = 31 * result + Objects.hashCode(this.resourceIncludePatterns);
      result = 31 * result + Objects.hashCode(this.resourceExcludePatterns);
      result = 31 * result + Objects.hashCode(this.testParallelism);
      result = 31 * result + Objects.hashCode(this.junitXmlReport);
      result = 31 * result + Objects.hashCode(this.testFrameworks);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(2600);
      builder.append(ScalaNativeTestModule.class.getSimpleName()).append(" {");
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "sources", this.sources);
      appendProperty(builder, "moduleDeps", this.moduleDeps);
      appendProperty(builder, "bspVisible", this.bspVisible);
      appendProperty(builder, "type", this.type);
      appendProperty(builder, "resources", this.resources);
      appendProperty(builder, "javaHome", this.javaHome);
      appendProperty(builder, "jvmOptions", this.jvmOptions);
      appendProperty(builder, "javaVersion", this.javaVersion);
      appendProperty(builder, "compileOrder", this.compileOrder);
      appendProperty(builder, "javacOptions", this.javacOptions);
      appendProperty(builder, "forkEnv", this.forkEnv);
      appendProperty(builder, "mainClass", this.mainClass);
      appendProperty(builder, "deps", this.deps);
      appendProperty(builder, "compileOnlyDeps", this.compileOnlyDeps);
      appendProperty(builder, "javacAnnotationProcessorDeps", this.javacAnnotationProcessorDeps);
      appendProperty(builder, "javaSemanticdbVersion", this.javaSemanticdbVersion);
      appendProperty(builder, "semanticdbEnabled", this.semanticdbEnabled);
      appendProperty(builder, "manifest", this.manifest);
      appendProperty(builder, "shadeRulesFile", this.shadeRulesFile);
      appendProperty(builder, "publish", this.publish);
      appendProperty(builder, "pomSettings", this.pomSettings);
      appendProperty(builder, "publishTo", this.publishTo);
      appendProperty(builder, "publishLocalTo", this.publishLocalTo);
      appendProperty(builder, "graalvm", this.graalvm);
      appendProperty(builder, "mvnApps", this.mvnApps);
      appendProperty(builder, "scalaVersion", this.scalaVersion);
      appendProperty(builder, "scalacOptions", this.scalacOptions);
      appendProperty(builder, "scalacPluginDeps", this.scalacPluginDeps);
      appendProperty(builder, "scalaSemanticdbVersion", this.scalaSemanticdbVersion);
      appendProperty(builder, "scalaNativeVersion", this.scalaNativeVersion);
      appendProperty(builder, "gc", this.gc);
      appendProperty(builder, "mode", this.mode);
      appendProperty(builder, "multithreading", this.multithreading);
      appendProperty(builder, "lto", this.lto);
      appendProperty(builder, "embedResources", this.embedResources);
      appendProperty(builder, "linkStubs", this.linkStubs);
      appendProperty(builder, "check", this.check);
      appendProperty(builder, "checkFatalWarnings", this.checkFatalWarnings);
      appendProperty(builder, "optimize", this.optimize);
      appendProperty(builder, "targetTriple", this.targetTriple);
      appendProperty(builder, "nativeLinkingOptions", this.nativeLinkingOptions);
      appendProperty(builder, "nativeCompileOptions", this.nativeCompileOptions);
      appendProperty(builder, "nativeCOptions", this.nativeCOptions);
      appendProperty(builder, "nativeCppOptions", this.nativeCppOptions);
      appendProperty(builder, "resourceIncludePatterns", this.resourceIncludePatterns);
      appendProperty(builder, "resourceExcludePatterns", this.resourceExcludePatterns);
      appendProperty(builder, "testParallelism", this.testParallelism);
      appendProperty(builder, "junitXmlReport", this.junitXmlReport);
      appendProperty(builder, "testFrameworks", this.testFrameworks);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class JavaModules {
    public final @NonNull JavaModule main;

    public final @NonNull JavaTestModule test;

    public final @NonNull List<? extends @NonNull DederModule> all;

    public JavaModules(@Named("main") @NonNull JavaModule main,
        @Named("test") @NonNull JavaTestModule test,
        @Named("all") @NonNull List<? extends @NonNull DederModule> all) {
      this.main = main;
      this.test = test;
      this.all = all;
    }

    public JavaModules withMain(@NonNull JavaModule main) {
      return new JavaModules(main, test, all);
    }

    public JavaModules withTest(@NonNull JavaTestModule test) {
      return new JavaModules(main, test, all);
    }

    public JavaModules withAll(@NonNull List<? extends @NonNull DederModule> all) {
      return new JavaModules(main, test, all);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      JavaModules other = (JavaModules) obj;
      if (!Objects.equals(this.main, other.main)) return false;
      if (!Objects.equals(this.test, other.test)) return false;
      if (!Objects.equals(this.all, other.all)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.main);
      result = 31 * result + Objects.hashCode(this.test);
      result = 31 * result + Objects.hashCode(this.all);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(JavaModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "main", this.main);
      appendProperty(builder, "test", this.test);
      appendProperty(builder, "all", this.all);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class CreateJavaModules {
    public final @NonNull String root;

    public final String id;

    public final @NonNull JavaModule template;

    public final @NonNull JavaTestModule testTemplate;

    public final @NonNull DirLayout layout;

    public final @NonNull JavaModules get;

    public CreateJavaModules(@Named("root") @NonNull String root, @Named("id") String id,
        @Named("template") @NonNull JavaModule template,
        @Named("testTemplate") @NonNull JavaTestModule testTemplate,
        @Named("layout") @NonNull DirLayout layout, @Named("get") @NonNull JavaModules get) {
      this.root = root;
      this.id = id;
      this.template = template;
      this.testTemplate = testTemplate;
      this.layout = layout;
      this.get = get;
    }

    public CreateJavaModules withRoot(@NonNull String root) {
      return new CreateJavaModules(root, id, template, testTemplate, layout, get);
    }

    public CreateJavaModules withId(String id) {
      return new CreateJavaModules(root, id, template, testTemplate, layout, get);
    }

    public CreateJavaModules withTemplate(@NonNull JavaModule template) {
      return new CreateJavaModules(root, id, template, testTemplate, layout, get);
    }

    public CreateJavaModules withTestTemplate(@NonNull JavaTestModule testTemplate) {
      return new CreateJavaModules(root, id, template, testTemplate, layout, get);
    }

    public CreateJavaModules withLayout(@NonNull DirLayout layout) {
      return new CreateJavaModules(root, id, template, testTemplate, layout, get);
    }

    public CreateJavaModules withGet(@NonNull JavaModules get) {
      return new CreateJavaModules(root, id, template, testTemplate, layout, get);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      CreateJavaModules other = (CreateJavaModules) obj;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.template, other.template)) return false;
      if (!Objects.equals(this.testTemplate, other.testTemplate)) return false;
      if (!Objects.equals(this.layout, other.layout)) return false;
      if (!Objects.equals(this.get, other.get)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.template);
      result = 31 * result + Objects.hashCode(this.testTemplate);
      result = 31 * result + Objects.hashCode(this.layout);
      result = 31 * result + Objects.hashCode(this.get);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(350);
      builder.append(CreateJavaModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "template", this.template);
      appendProperty(builder, "testTemplate", this.testTemplate);
      appendProperty(builder, "layout", this.layout);
      appendProperty(builder, "get", this.get);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaModules {
    public final @NonNull ScalaModule main;

    public final @NonNull ScalaTestModule test;

    public final @NonNull List<? extends @NonNull DederModule> all;

    public ScalaModules(@Named("main") @NonNull ScalaModule main,
        @Named("test") @NonNull ScalaTestModule test,
        @Named("all") @NonNull List<? extends @NonNull DederModule> all) {
      this.main = main;
      this.test = test;
      this.all = all;
    }

    public ScalaModules withMain(@NonNull ScalaModule main) {
      return new ScalaModules(main, test, all);
    }

    public ScalaModules withTest(@NonNull ScalaTestModule test) {
      return new ScalaModules(main, test, all);
    }

    public ScalaModules withAll(@NonNull List<? extends @NonNull DederModule> all) {
      return new ScalaModules(main, test, all);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaModules other = (ScalaModules) obj;
      if (!Objects.equals(this.main, other.main)) return false;
      if (!Objects.equals(this.test, other.test)) return false;
      if (!Objects.equals(this.all, other.all)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.main);
      result = 31 * result + Objects.hashCode(this.test);
      result = 31 * result + Objects.hashCode(this.all);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(ScalaModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "main", this.main);
      appendProperty(builder, "test", this.test);
      appendProperty(builder, "all", this.all);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class CreateScalaModules {
    public final @NonNull String root;

    public final String id;

    public final String testId;

    public final @NonNull ScalaModule template;

    public final @NonNull ScalaTestModule testTemplate;

    public final @NonNull DirLayout layout;

    public final @NonNull ScalaModules get;

    public CreateScalaModules(@Named("root") @NonNull String root, @Named("id") String id,
        @Named("testId") String testId, @Named("template") @NonNull ScalaModule template,
        @Named("testTemplate") @NonNull ScalaTestModule testTemplate,
        @Named("layout") @NonNull DirLayout layout, @Named("get") @NonNull ScalaModules get) {
      this.root = root;
      this.id = id;
      this.testId = testId;
      this.template = template;
      this.testTemplate = testTemplate;
      this.layout = layout;
      this.get = get;
    }

    public CreateScalaModules withRoot(@NonNull String root) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    public CreateScalaModules withId(String id) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    public CreateScalaModules withTestId(String testId) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    public CreateScalaModules withTemplate(@NonNull ScalaModule template) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    public CreateScalaModules withTestTemplate(@NonNull ScalaTestModule testTemplate) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    public CreateScalaModules withLayout(@NonNull DirLayout layout) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    public CreateScalaModules withGet(@NonNull ScalaModules get) {
      return new CreateScalaModules(root, id, testId, template, testTemplate, layout, get);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      CreateScalaModules other = (CreateScalaModules) obj;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.testId, other.testId)) return false;
      if (!Objects.equals(this.template, other.template)) return false;
      if (!Objects.equals(this.testTemplate, other.testTemplate)) return false;
      if (!Objects.equals(this.layout, other.layout)) return false;
      if (!Objects.equals(this.get, other.get)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.testId);
      result = 31 * result + Objects.hashCode(this.template);
      result = 31 * result + Objects.hashCode(this.testTemplate);
      result = 31 * result + Objects.hashCode(this.layout);
      result = 31 * result + Objects.hashCode(this.get);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(400);
      builder.append(CreateScalaModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "testId", this.testId);
      appendProperty(builder, "template", this.template);
      appendProperty(builder, "testTemplate", this.testTemplate);
      appendProperty(builder, "layout", this.layout);
      appendProperty(builder, "get", this.get);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaJsModules {
    public final @NonNull ScalaJsModule main;

    public final @NonNull ScalaJsTestModule test;

    public final @NonNull List<? extends @NonNull DederModule> all;

    public ScalaJsModules(@Named("main") @NonNull ScalaJsModule main,
        @Named("test") @NonNull ScalaJsTestModule test,
        @Named("all") @NonNull List<? extends @NonNull DederModule> all) {
      this.main = main;
      this.test = test;
      this.all = all;
    }

    public ScalaJsModules withMain(@NonNull ScalaJsModule main) {
      return new ScalaJsModules(main, test, all);
    }

    public ScalaJsModules withTest(@NonNull ScalaJsTestModule test) {
      return new ScalaJsModules(main, test, all);
    }

    public ScalaJsModules withAll(@NonNull List<? extends @NonNull DederModule> all) {
      return new ScalaJsModules(main, test, all);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaJsModules other = (ScalaJsModules) obj;
      if (!Objects.equals(this.main, other.main)) return false;
      if (!Objects.equals(this.test, other.test)) return false;
      if (!Objects.equals(this.all, other.all)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.main);
      result = 31 * result + Objects.hashCode(this.test);
      result = 31 * result + Objects.hashCode(this.all);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(ScalaJsModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "main", this.main);
      appendProperty(builder, "test", this.test);
      appendProperty(builder, "all", this.all);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class CreateScalaJsModules {
    public final @NonNull String root;

    public final String id;

    public final @NonNull ScalaJsModule template;

    public final @NonNull ScalaJsTestModule testTemplate;

    public final @NonNull DirLayout layout;

    public final @NonNull ScalaJsModules get;

    public CreateScalaJsModules(@Named("root") @NonNull String root, @Named("id") String id,
        @Named("template") @NonNull ScalaJsModule template,
        @Named("testTemplate") @NonNull ScalaJsTestModule testTemplate,
        @Named("layout") @NonNull DirLayout layout, @Named("get") @NonNull ScalaJsModules get) {
      this.root = root;
      this.id = id;
      this.template = template;
      this.testTemplate = testTemplate;
      this.layout = layout;
      this.get = get;
    }

    public CreateScalaJsModules withRoot(@NonNull String root) {
      return new CreateScalaJsModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaJsModules withId(String id) {
      return new CreateScalaJsModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaJsModules withTemplate(@NonNull ScalaJsModule template) {
      return new CreateScalaJsModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaJsModules withTestTemplate(@NonNull ScalaJsTestModule testTemplate) {
      return new CreateScalaJsModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaJsModules withLayout(@NonNull DirLayout layout) {
      return new CreateScalaJsModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaJsModules withGet(@NonNull ScalaJsModules get) {
      return new CreateScalaJsModules(root, id, template, testTemplate, layout, get);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      CreateScalaJsModules other = (CreateScalaJsModules) obj;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.template, other.template)) return false;
      if (!Objects.equals(this.testTemplate, other.testTemplate)) return false;
      if (!Objects.equals(this.layout, other.layout)) return false;
      if (!Objects.equals(this.get, other.get)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.template);
      result = 31 * result + Objects.hashCode(this.testTemplate);
      result = 31 * result + Objects.hashCode(this.layout);
      result = 31 * result + Objects.hashCode(this.get);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(350);
      builder.append(CreateScalaJsModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "template", this.template);
      appendProperty(builder, "testTemplate", this.testTemplate);
      appendProperty(builder, "layout", this.layout);
      appendProperty(builder, "get", this.get);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class ScalaNativeModules {
    public final @NonNull ScalaNativeModule main;

    public final @NonNull ScalaNativeTestModule test;

    public final @NonNull List<? extends @NonNull DederModule> all;

    public ScalaNativeModules(@Named("main") @NonNull ScalaNativeModule main,
        @Named("test") @NonNull ScalaNativeTestModule test,
        @Named("all") @NonNull List<? extends @NonNull DederModule> all) {
      this.main = main;
      this.test = test;
      this.all = all;
    }

    public ScalaNativeModules withMain(@NonNull ScalaNativeModule main) {
      return new ScalaNativeModules(main, test, all);
    }

    public ScalaNativeModules withTest(@NonNull ScalaNativeTestModule test) {
      return new ScalaNativeModules(main, test, all);
    }

    public ScalaNativeModules withAll(@NonNull List<? extends @NonNull DederModule> all) {
      return new ScalaNativeModules(main, test, all);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      ScalaNativeModules other = (ScalaNativeModules) obj;
      if (!Objects.equals(this.main, other.main)) return false;
      if (!Objects.equals(this.test, other.test)) return false;
      if (!Objects.equals(this.all, other.all)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.main);
      result = 31 * result + Objects.hashCode(this.test);
      result = 31 * result + Objects.hashCode(this.all);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(200);
      builder.append(ScalaNativeModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "main", this.main);
      appendProperty(builder, "test", this.test);
      appendProperty(builder, "all", this.all);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class CreateScalaNativeModules {
    public final @NonNull String root;

    public final String id;

    public final @NonNull ScalaNativeModule template;

    public final @NonNull ScalaNativeTestModule testTemplate;

    public final @NonNull DirLayout layout;

    public final @NonNull ScalaNativeModules get;

    public CreateScalaNativeModules(@Named("root") @NonNull String root, @Named("id") String id,
        @Named("template") @NonNull ScalaNativeModule template,
        @Named("testTemplate") @NonNull ScalaNativeTestModule testTemplate,
        @Named("layout") @NonNull DirLayout layout, @Named("get") @NonNull ScalaNativeModules get) {
      this.root = root;
      this.id = id;
      this.template = template;
      this.testTemplate = testTemplate;
      this.layout = layout;
      this.get = get;
    }

    public CreateScalaNativeModules withRoot(@NonNull String root) {
      return new CreateScalaNativeModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaNativeModules withId(String id) {
      return new CreateScalaNativeModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaNativeModules withTemplate(@NonNull ScalaNativeModule template) {
      return new CreateScalaNativeModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaNativeModules withTestTemplate(@NonNull ScalaNativeTestModule testTemplate) {
      return new CreateScalaNativeModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaNativeModules withLayout(@NonNull DirLayout layout) {
      return new CreateScalaNativeModules(root, id, template, testTemplate, layout, get);
    }

    public CreateScalaNativeModules withGet(@NonNull ScalaNativeModules get) {
      return new CreateScalaNativeModules(root, id, template, testTemplate, layout, get);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      CreateScalaNativeModules other = (CreateScalaNativeModules) obj;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.template, other.template)) return false;
      if (!Objects.equals(this.testTemplate, other.testTemplate)) return false;
      if (!Objects.equals(this.layout, other.layout)) return false;
      if (!Objects.equals(this.get, other.get)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.template);
      result = 31 * result + Objects.hashCode(this.testTemplate);
      result = 31 * result + Objects.hashCode(this.layout);
      result = 31 * result + Objects.hashCode(this.get);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(350);
      builder.append(CreateScalaNativeModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "template", this.template);
      appendProperty(builder, "testTemplate", this.testTemplate);
      appendProperty(builder, "layout", this.layout);
      appendProperty(builder, "get", this.get);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class CrossModules {
    public final @NonNull ScalaModule jvm;

    public final @NonNull ScalaTestModule jvm_test;

    public final @NonNull ScalaJsModule js;

    public final @NonNull ScalaJsTestModule js_test;

    public final @NonNull ScalaNativeModule _native;

    public final @NonNull ScalaNativeTestModule native_test;

    public final @NonNull List<? extends @NonNull DederModule> all;

    public CrossModules(@Named("jvm") @NonNull ScalaModule jvm,
        @Named("jvm_test") @NonNull ScalaTestModule jvm_test,
        @Named("js") @NonNull ScalaJsModule js,
        @Named("js_test") @NonNull ScalaJsTestModule js_test,
        @Named("native") @NonNull ScalaNativeModule _native,
        @Named("native_test") @NonNull ScalaNativeTestModule native_test,
        @Named("all") @NonNull List<? extends @NonNull DederModule> all) {
      this.jvm = jvm;
      this.jvm_test = jvm_test;
      this.js = js;
      this.js_test = js_test;
      this._native = _native;
      this.native_test = native_test;
      this.all = all;
    }

    public CrossModules withJvm(@NonNull ScalaModule jvm) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    public CrossModules withJvm_test(@NonNull ScalaTestModule jvm_test) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    public CrossModules withJs(@NonNull ScalaJsModule js) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    public CrossModules withJs_test(@NonNull ScalaJsTestModule js_test) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    public CrossModules withNative(@NonNull ScalaNativeModule _native) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    public CrossModules withNative_test(@NonNull ScalaNativeTestModule native_test) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    public CrossModules withAll(@NonNull List<? extends @NonNull DederModule> all) {
      return new CrossModules(jvm, jvm_test, js, js_test, _native, native_test, all);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      CrossModules other = (CrossModules) obj;
      if (!Objects.equals(this.jvm, other.jvm)) return false;
      if (!Objects.equals(this.jvm_test, other.jvm_test)) return false;
      if (!Objects.equals(this.js, other.js)) return false;
      if (!Objects.equals(this.js_test, other.js_test)) return false;
      if (!Objects.equals(this._native, other._native)) return false;
      if (!Objects.equals(this.native_test, other.native_test)) return false;
      if (!Objects.equals(this.all, other.all)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.jvm);
      result = 31 * result + Objects.hashCode(this.jvm_test);
      result = 31 * result + Objects.hashCode(this.js);
      result = 31 * result + Objects.hashCode(this.js_test);
      result = 31 * result + Objects.hashCode(this._native);
      result = 31 * result + Objects.hashCode(this.native_test);
      result = 31 * result + Objects.hashCode(this.all);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(400);
      builder.append(CrossModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "jvm", this.jvm);
      appendProperty(builder, "jvm_test", this.jvm_test);
      appendProperty(builder, "js", this.js);
      appendProperty(builder, "js_test", this.js_test);
      appendProperty(builder, "_native", this._native);
      appendProperty(builder, "native_test", this.native_test);
      appendProperty(builder, "all", this.all);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class CreateCrossModules {
    public final @NonNull ScalaModule template;

    public final @NonNull ScalaTestModule testTemplate;

    public final @NonNull ScalaJsModule jsTemplate;

    public final @NonNull ScalaJsTestModule jsTestTemplate;

    public final @NonNull ScalaNativeModule nativeTemplate;

    public final @NonNull ScalaNativeTestModule nativeTestTemplate;

    public final @NonNull String root;

    public final String id;

    public final @NonNull DirLayout layout;

    public final @NonNull CrossModules get;

    public CreateCrossModules(@Named("template") @NonNull ScalaModule template,
        @Named("testTemplate") @NonNull ScalaTestModule testTemplate,
        @Named("jsTemplate") @NonNull ScalaJsModule jsTemplate,
        @Named("jsTestTemplate") @NonNull ScalaJsTestModule jsTestTemplate,
        @Named("nativeTemplate") @NonNull ScalaNativeModule nativeTemplate,
        @Named("nativeTestTemplate") @NonNull ScalaNativeTestModule nativeTestTemplate,
        @Named("root") @NonNull String root, @Named("id") String id,
        @Named("layout") @NonNull DirLayout layout, @Named("get") @NonNull CrossModules get) {
      this.template = template;
      this.testTemplate = testTemplate;
      this.jsTemplate = jsTemplate;
      this.jsTestTemplate = jsTestTemplate;
      this.nativeTemplate = nativeTemplate;
      this.nativeTestTemplate = nativeTestTemplate;
      this.root = root;
      this.id = id;
      this.layout = layout;
      this.get = get;
    }

    public CreateCrossModules withTemplate(@NonNull ScalaModule template) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withTestTemplate(@NonNull ScalaTestModule testTemplate) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withJsTemplate(@NonNull ScalaJsModule jsTemplate) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withJsTestTemplate(@NonNull ScalaJsTestModule jsTestTemplate) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withNativeTemplate(@NonNull ScalaNativeModule nativeTemplate) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withNativeTestTemplate(
        @NonNull ScalaNativeTestModule nativeTestTemplate) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withRoot(@NonNull String root) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withId(String id) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withLayout(@NonNull DirLayout layout) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    public CreateCrossModules withGet(@NonNull CrossModules get) {
      return new CreateCrossModules(template, testTemplate, jsTemplate, jsTestTemplate, nativeTemplate, nativeTestTemplate, root, id, layout, get);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      CreateCrossModules other = (CreateCrossModules) obj;
      if (!Objects.equals(this.template, other.template)) return false;
      if (!Objects.equals(this.testTemplate, other.testTemplate)) return false;
      if (!Objects.equals(this.jsTemplate, other.jsTemplate)) return false;
      if (!Objects.equals(this.jsTestTemplate, other.jsTestTemplate)) return false;
      if (!Objects.equals(this.nativeTemplate, other.nativeTemplate)) return false;
      if (!Objects.equals(this.nativeTestTemplate, other.nativeTestTemplate)) return false;
      if (!Objects.equals(this.root, other.root)) return false;
      if (!Objects.equals(this.id, other.id)) return false;
      if (!Objects.equals(this.layout, other.layout)) return false;
      if (!Objects.equals(this.get, other.get)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.template);
      result = 31 * result + Objects.hashCode(this.testTemplate);
      result = 31 * result + Objects.hashCode(this.jsTemplate);
      result = 31 * result + Objects.hashCode(this.jsTestTemplate);
      result = 31 * result + Objects.hashCode(this.nativeTemplate);
      result = 31 * result + Objects.hashCode(this.nativeTestTemplate);
      result = 31 * result + Objects.hashCode(this.root);
      result = 31 * result + Objects.hashCode(this.id);
      result = 31 * result + Objects.hashCode(this.layout);
      result = 31 * result + Objects.hashCode(this.get);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(550);
      builder.append(CreateCrossModules.class.getSimpleName()).append(" {");
      appendProperty(builder, "template", this.template);
      appendProperty(builder, "testTemplate", this.testTemplate);
      appendProperty(builder, "jsTemplate", this.jsTemplate);
      appendProperty(builder, "jsTestTemplate", this.jsTestTemplate);
      appendProperty(builder, "nativeTemplate", this.nativeTemplate);
      appendProperty(builder, "nativeTestTemplate", this.nativeTestTemplate);
      appendProperty(builder, "root", this.root);
      appendProperty(builder, "id", this.id);
      appendProperty(builder, "layout", this.layout);
      appendProperty(builder, "get", this.get);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public enum ModuleType {
    JAVA("java"),

    JAVA_TEST("java-test"),

    SCALA("scala"),

    SCALA_TEST("scala-test"),

    SCALA_JS("scala-js"),

    SCALA_JS_TEST("scala-js-test"),

    SCALA_NATIVE("scala-native"),

    SCALA_NATIVE_TEST("scala-native-test");

    private String value;

    private ModuleType(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum CompileOrder {
    JAVA_THEN_SCALA("java-then-scala"),

    SCALA_THEN_JAVA("scala-then-java"),

    MIXED("mixed");

    private String value;

    private CompileOrder(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum DirLayout {
    DEFAULT("default"),

    MAVEN("maven"),

    SBT("sbt"),

    SBT_CROSS_FULL("sbt-cross-full"),

    SBT_CROSS_PURE("sbt-cross-pure"),

    SBT_CROSS_DUMMY("sbt-cross-dummy");

    private String value;

    private DirLayout(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaBinaryVersion {
    _2_12("2.12"),

    _2_13("2.13"),

    _3("3");

    private String value;

    private ScalaBinaryVersion(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaJsModuleKind {
    NO_MODULE("no-module"),

    ES_MODULE("es-module"),

    COMMONJS_MODULE("commonjs-module");

    private String value;

    private ScalaJsModuleKind(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaJsModuleSplitStyle {
    FEWEST_MODULES("fewest-modules"),

    SMALLEST_MODULES("smallest-modules"),

    SMALL_MODULES_FOR("small-modules-for");

    private String value;

    private ScalaJsModuleSplitStyle(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaJsESVersion {
    ES2015("es2015"),

    ES2016("es2016"),

    ES2017("es2017"),

    ES2018("es2018"),

    ES2019("es2019"),

    ES2020("es2020"),

    ES2021("es2021");

    private String value;

    private ScalaJsESVersion(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaNativeGC {
    NONE("none"),

    BOEHM("boehm"),

    IMMIX("immix"),

    COMMIX("commix");

    private String value;

    private ScalaNativeGC(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaNativeMode {
    DEBUG("debug"),

    RELEASE_FAST("release-fast"),

    RELEASE_FULL("release-full"),

    RELEASE_SIZE("release-size");

    private String value;

    private ScalaNativeMode(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }

  public enum ScalaNativeLTO {
    NONE("none"),

    THIN("thin"),

    FULL("full");

    private String value;

    private ScalaNativeLTO(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return this.value;
    }
  }
}
