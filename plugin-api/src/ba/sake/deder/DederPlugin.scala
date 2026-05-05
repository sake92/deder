package ba.sake.deder

import ba.sake.deder.deps.Dependency

/** Trait that plugins implement to register additional tasks. */
trait DederPlugin {
  def id: String

  /** @param coreTasks  Access to built-in server tasks (compile, classes, deps, etc.)
   *  @param configText The plugin's configuration as a Pkl expression string.
   *                    Evaluate it with Pkl to get your typed config object:
   *                    {{{
   *                    val evaluator = org.pkl.config.java.ConfigEvaluator.preconfigured
   *                    val config = evaluator.evaluate(
   *                      org.pkl.core.ModuleSource.text(configText)
   *                    ).as(classOf[MyConfig])
   *                    }}}
   */
  def tasks(coreTasks: CoreTasksApi, configText: String): Seq[AbstractTask[?]]
}

/** Typed access to built-in server tasks, available to plugins as dependency targets. */
trait CoreTasksApi {
  def sourcesTask: AbstractTask[Seq[DederPath]]
  def sourceFilesTask: AbstractTask[Seq[DederPath]]
  def resourcesTask: AbstractTask[Seq[DederPath]]
  def classesTask: AbstractTask[os.Path]
  def allClassesDirsTask: AbstractTask[Seq[os.Path]]
  def compileTask: AbstractTask[DederPath]
  def allDependenciesTask: AbstractTask[Seq[Dependency]]
  def compileClasspathTask: AbstractTask[Seq[os.Path]]
}
