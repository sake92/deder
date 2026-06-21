package ba.sake.deder

import ba.sake.tupson.{*, given}
import ba.sake.deder.config.DederProject.ModuleType
import org.typelevel.jawn.ast.{JValue, JString}

given JsonRW[ModuleType] with
  def write(value: ModuleType): JValue = JString(value.name())
  def parse(path: String, jValue: JValue): ModuleType =
    jValue match
      case JString(s) =>
        try ModuleType.valueOf(s)
        catch case _: IllegalArgumentException =>
          throw ParsingException(ParseError(path, s"Invalid ModuleType: $s"))
      case _ => throw ParsingException(ParseError(path, "Expected a string for ModuleType"))

trait TasksRegistryApi {
  /** All registered tasks (built-in + plugins), across all modules. */
  def allTasks: Seq[TaskInfo]

  /** Tasks compatible with a given module type.
    * Includes tasks whose `supportedModuleTypes` is empty (all types) or contains the given type.
    * Does NOT apply per-module `enabled` filtering — that requires a specific `DederModule`.
    */
  def tasksFor(moduleType: ModuleType): Seq[TaskInfo]
}

case class TaskInfo(
    name: String,
    description: String,
    category: String,
    kind: TaskKind,
    supportedModuleTypes: Seq[ModuleType],
    transitive: Boolean,
    singleton: Boolean,
    internal: Boolean,
    featureTags: Seq[FeatureTag]
) derives JsonRW
