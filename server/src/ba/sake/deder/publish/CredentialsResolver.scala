package ba.sake.deder.publish

import ba.sake.deder.config.DederProject.*
import ba.sake.deder.config.DederCredentials.*

object CredentialsResolver {

  /** Resolve publish credentials with env var overrides.
    *
    * Precedence (highest to lowest):
    * 1. Client env vars from CLI (RequestContext.clientParams)
    * 2. System env vars (sys.env / System.getenv)
    * 3. ~/.deder/credentials.pkl (fallback)
    *
    * Env vars follow convention: DEDER_<REPO_ID>_<FIELD>
    * where REPO_ID = id uppercased, hyphens → underscores
    * and FIELD = field name uppercased (USERNAME, PASSWORD, etc.)
    *
    * All fields can be provided via env vars alone; credentials.pkl is optional
    * if env vars cover all required fields.
    *
    * @param publishTo       The publish target from deder.pkl
    * @param credentialsOpt  Parsed credentials from ~/.deder/credentials.pkl (None if file missing)
    * @param clientEnv       Client-side env vars (from RequestContext.clientParams)
    * @param sysEnv          Server-side system env vars (sys.env / System.getenv)
    * @return                Fully resolved RepoCredentials
    * @throws RuntimeException if any required field is missing from all sources
    */
  def resolve(
    publishTo: PublishRepo,
    credentialsOpt: Option[DederCredentials],
    clientEnv: Map[String, String],
    sysEnv: Map[String, String]
  ): RepoCredentials = {
    val prefix = envPrefix(publishTo.id)

    def field(name: String, pklValue: => Option[String]): String = {
      val envKey = prefix + name.toUpperCase
      clientEnv.get(envKey)
        .orElse(sysEnv.get(envKey))
        .orElse(pklValue.filter(_.nonEmpty))
        .getOrElse {
          throw new RuntimeException(
            s"Missing credential '${name}' for repo '${publishTo.id}'. " +
            s"Set ${envKey} environment variable, or add to ~/.deder/credentials.pkl:\n" +
            s"  [\"${publishTo.id}\"] = new ${expectedCredentialType(publishTo)} { ${name} = \"...\" }"
          )
        }
    }

    publishTo match {
      case _: SonatypeCentralRepo =>
        val pkl = credentialsOpt.flatMap(getCreds[SonatypeCentralCredentials](_, publishTo.id))
        new SonatypeCentralCredentials(
          field("username", pkl.map(_.username)),
          field("password", pkl.map(_.password)),
          field("pgpSecret", pkl.map(_.pgpSecret)),
          field("pgpPassphrase", pkl.map(_.pgpPassphrase))
        )

      case _: SonatypeSnapshotRepo =>
        val pkl = credentialsOpt.flatMap(getCreds[SonatypeSnapshotCredentials](_, publishTo.id))
        new SonatypeSnapshotCredentials(
          field("username", pkl.map(_.username)),
          field("password", pkl.map(_.password))
        )

      case mavenRepo: MavenRepo =>
        val pkl = credentialsOpt.flatMap(getCreds[BasicAuthCredentials](_, publishTo.id))
        new BasicAuthCredentials(
          field("username", pkl.map(_.username)),
          field("password", pkl.map(_.password))
        )
    }
  }

  private def envPrefix(repoId: String): String =
    "DEDER_" + repoId.toUpperCase.replace('-', '_') + "_"

  private def getCreds[T <: RepoCredentials](
    creds: DederCredentials,
    repoId: String
  ): Option[T] = {
    import scala.jdk.CollectionConverters.*
    creds.credentials.asScala.get(repoId) match {
      case Some(c: T) => Some(c)
      case _          => None
    }
  }

  private def expectedCredentialType(publishTo: PublishRepo): String = publishTo match {
    case _: SonatypeCentralRepo  => "SonatypeCentralCredentials"
    case _: SonatypeSnapshotRepo => "SonatypeSnapshotCredentials"
    case _: MavenRepo            => "BasicAuthCredentials"
  }
}
