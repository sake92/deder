package ba.sake.deder.config;

import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.util.Map;
import java.util.Objects;
import org.pkl.config.java.mapper.Named;
import org.pkl.config.java.mapper.NonNull;

public final class DederCredentials {
  public final @NonNull Map<@NonNull String, ? extends @NonNull RepoCredentials> credentials;

  public DederCredentials(
      @Named("credentials") @NonNull Map<@NonNull String, ? extends @NonNull RepoCredentials> credentials) {
    this.credentials = credentials;
  }

  public DederCredentials withCredentials(
      @NonNull Map<@NonNull String, ? extends @NonNull RepoCredentials> credentials) {
    return new DederCredentials(credentials);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (this.getClass() != obj.getClass()) return false;
    DederCredentials other = (DederCredentials) obj;
    if (!Objects.equals(this.credentials, other.credentials)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + Objects.hashCode(this.credentials);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(100);
    builder.append(DederCredentials.class.getSimpleName()).append(" {");
    appendProperty(builder, "credentials", this.credentials);
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

  public abstract static class RepoCredentials {
    protected RepoCredentials() {
    }
  }

  public static final class SonatypeCentralCredentials extends RepoCredentials {
    public final @NonNull String username;

    public final @NonNull String password;

    public final @NonNull String pgpSecret;

    public final @NonNull String pgpPassphrase;

    public SonatypeCentralCredentials(@Named("username") @NonNull String username,
        @Named("password") @NonNull String password, @Named("pgpSecret") @NonNull String pgpSecret,
        @Named("pgpPassphrase") @NonNull String pgpPassphrase) {
      this.username = username;
      this.password = password;
      this.pgpSecret = pgpSecret;
      this.pgpPassphrase = pgpPassphrase;
    }

    public SonatypeCentralCredentials withUsername(@NonNull String username) {
      return new SonatypeCentralCredentials(username, password, pgpSecret, pgpPassphrase);
    }

    public SonatypeCentralCredentials withPassword(@NonNull String password) {
      return new SonatypeCentralCredentials(username, password, pgpSecret, pgpPassphrase);
    }

    public SonatypeCentralCredentials withPgpSecret(@NonNull String pgpSecret) {
      return new SonatypeCentralCredentials(username, password, pgpSecret, pgpPassphrase);
    }

    public SonatypeCentralCredentials withPgpPassphrase(@NonNull String pgpPassphrase) {
      return new SonatypeCentralCredentials(username, password, pgpSecret, pgpPassphrase);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      SonatypeCentralCredentials other = (SonatypeCentralCredentials) obj;
      if (!Objects.equals(this.username, other.username)) return false;
      if (!Objects.equals(this.password, other.password)) return false;
      if (!Objects.equals(this.pgpSecret, other.pgpSecret)) return false;
      if (!Objects.equals(this.pgpPassphrase, other.pgpPassphrase)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.username);
      result = 31 * result + Objects.hashCode(this.password);
      result = 31 * result + Objects.hashCode(this.pgpSecret);
      result = 31 * result + Objects.hashCode(this.pgpPassphrase);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(250);
      builder.append(SonatypeCentralCredentials.class.getSimpleName()).append(" {");
      appendProperty(builder, "username", this.username);
      appendProperty(builder, "password", this.password);
      appendProperty(builder, "pgpSecret", this.pgpSecret);
      appendProperty(builder, "pgpPassphrase", this.pgpPassphrase);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class SonatypeSnapshotCredentials extends RepoCredentials {
    public final @NonNull String username;

    public final @NonNull String password;

    public SonatypeSnapshotCredentials(@Named("username") @NonNull String username,
        @Named("password") @NonNull String password) {
      this.username = username;
      this.password = password;
    }

    public SonatypeSnapshotCredentials withUsername(@NonNull String username) {
      return new SonatypeSnapshotCredentials(username, password);
    }

    public SonatypeSnapshotCredentials withPassword(@NonNull String password) {
      return new SonatypeSnapshotCredentials(username, password);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      SonatypeSnapshotCredentials other = (SonatypeSnapshotCredentials) obj;
      if (!Objects.equals(this.username, other.username)) return false;
      if (!Objects.equals(this.password, other.password)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.username);
      result = 31 * result + Objects.hashCode(this.password);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(SonatypeSnapshotCredentials.class.getSimpleName()).append(" {");
      appendProperty(builder, "username", this.username);
      appendProperty(builder, "password", this.password);
      builder.append("\n}");
      return builder.toString();
    }
  }

  public static final class BasicAuthCredentials extends RepoCredentials {
    public final @NonNull String username;

    public final @NonNull String password;

    public BasicAuthCredentials(@Named("username") @NonNull String username,
        @Named("password") @NonNull String password) {
      this.username = username;
      this.password = password;
    }

    public BasicAuthCredentials withUsername(@NonNull String username) {
      return new BasicAuthCredentials(username, password);
    }

    public BasicAuthCredentials withPassword(@NonNull String password) {
      return new BasicAuthCredentials(username, password);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (this.getClass() != obj.getClass()) return false;
      BasicAuthCredentials other = (BasicAuthCredentials) obj;
      if (!Objects.equals(this.username, other.username)) return false;
      if (!Objects.equals(this.password, other.password)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = 1;
      result = 31 * result + Objects.hashCode(this.username);
      result = 31 * result + Objects.hashCode(this.password);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder(150);
      builder.append(BasicAuthCredentials.class.getSimpleName()).append(" {");
      appendProperty(builder, "username", this.username);
      appendProperty(builder, "password", this.password);
      builder.append("\n}");
      return builder.toString();
    }
  }
}
