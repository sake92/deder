package ba.sake.deder

import mainargs.{Leftover, Parser}

@main def tabCompleterMain: Unit = {
  val cmd = "deder exec -t "
  println(
    TabCompleter.shellSplit(cmd, cmd.length).toString()
  )
}

class TabCompleter(tasksResolver: TasksResolver) {

  private val allSubcommands = Seq(
    "version",
    "clean",
    "modules",
    "tasks",
    "plan",
    "exec",
    "shutdown",
    "import",
    "complete",
    "help"
  )

  // TODO extract as param for easier testing
  private val allModuleIds = tasksResolver.allModules.map(_.id)
  private val allTaskIds = tasksResolver.taskInstancesPerModule.values.flatten.map(_.task.name).toSeq.distinct

  def complete(commandLine: String, cursorPos: Int): Seq[String] = {
    val (args, wordPos) = TabCompleter.shellSplit(commandLine, cursorPos)
    val currentWord = if (wordPos >= 0 && wordPos < args.length) args(wordPos) else ""
    val prevWord = if (wordPos >= 1 && wordPos < args.length) args(wordPos - 1) else ""
    val res = args match {
      case Seq("deder", rest*) =>
        rest match {
          case Seq("version", _*) => Seq.empty
          case Seq("clean", _*) =>
            if prevWord == "-m" || prevWord == "--module" then allModuleIds.filter(_.startsWith(currentWord))
            else Seq.empty
          case Seq("modules", _*) => Seq.empty
          case Seq("tasks", _*) =>
            if prevWord == "-m" || prevWord == "--module" then allModuleIds.filter(_.startsWith(currentWord))
            else Seq.empty
          case Seq("plan", _*) =>
            if prevWord == "-m" || prevWord == "--module" then allModuleIds.filter(_.startsWith(currentWord))
            else if prevWord == "-t" || prevWord == "--task" then allTaskIds.filter(_.startsWith(currentWord))
            else Seq.empty
          case Seq("exec", _*) =>
            if prevWord == "-m" || prevWord == "--module" then allModuleIds.filter(_.startsWith(currentWord))
            else if prevWord == "-t" || prevWord == "--task" then
              {
                allTaskIds
              }.filter(_.startsWith(currentWord))
            else Seq.empty
          case Seq("shutdown", _*) => Seq.empty
          case Seq("import", _*)   => Seq.empty
          case Seq("complete", _*) => Seq.empty
          case Seq("help", _*)     => Seq.empty
          case Seq(first, _*)      => allSubcommands
          case _                   => allSubcommands
        }
      case _ => Seq.empty
    }
    res
  }

}

object TabCompleter {

  def shellSplit(commandLine: String, cursorPos: Int): (Seq[String], Int) = {
    val tokens = scala.collection.mutable.ListBuffer.empty[String]
    val current = new StringBuilder()
    var inDoubleQuote = false
    var inSingleQuote = false
    var escaped = false
    var wordIndex = -1
    var currentWordIndex = -1
    var isCurrentWord = false

    for ((char, i) <- commandLine.zipWithIndex) {
      if i == cursorPos then {
        isCurrentWord = true
      }
      if (escaped) {
        current.append(char)
        escaped = false
      } else if (char == '\\' && !inSingleQuote) {
        escaped = true
      } else if (char == '\"' && !inSingleQuote) {
        inDoubleQuote = !inDoubleQuote
      } else if (char == '\'' && !inDoubleQuote) {
        inSingleQuote = !inSingleQuote
      } else if (char.isWhitespace && !inDoubleQuote && !inSingleQuote) {
        if (current.nonEmpty) {
          wordIndex += 1
          if isCurrentWord then {
            currentWordIndex = wordIndex
            isCurrentWord = false
          }
          tokens += current.toString()
          current.clear()
        }
      } else {
        current.append(char)
      }
    }
    if (current.nonEmpty) tokens += current.toString()
    if currentWordIndex == -1 && cursorPos == commandLine.length then {
      // cursor is at the end but we didnt find the current word
      commandLine.lastOption match {
        case Some(' ') => tokens += "" // add dummy token
        case _         =>
      }
      currentWordIndex = tokens.length - 1
    }
    tokens.result() -> currentWordIndex
  }
}
