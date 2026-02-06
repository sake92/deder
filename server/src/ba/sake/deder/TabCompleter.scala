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
          case Seq("clean", _*)   => completeModule(prevWord, currentWord).getOrElse(Seq.empty)
          case Seq("modules", _*) => Seq.empty
          case Seq("tasks", _*)   => completeModule(prevWord, currentWord).getOrElse(Seq.empty)
          case Seq("plan", _*) =>
            completeModule(prevWord, currentWord)
              .orElse(completeTask(prevWord, currentWord))
              .getOrElse(Seq.empty)
          case Seq("exec", _*) =>
            completeModule(prevWord, currentWord)
              .orElse(completeTask(prevWord, currentWord))
              .getOrElse(Seq.empty)
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

  private def completeModule(prevWord: String, currentWord: String): Option[Seq[String]] =
    Option.when(prevWord == "-m" || prevWord == "--module")(allModuleIds.filter(_.startsWith(currentWord)))

  private def completeTask(prevWord: String, currentWord: String): Option[Seq[String]] =
    Option.when(prevWord == "-t" || prevWord == "--task")(allTaskIds.filter(_.startsWith(currentWord)))
}

object TabCompleter {

  val bashScript: String =
    """|_deder_completion() {
       |    local cur line point completions
       |    cur="${COMP_WORDS[COMP_CWORD]}"
       |    line="${COMP_LINE}"
       |    point="${COMP_POINT}"
       |    completions=$(deder complete -s bash -c "$line" -p "$point" 2>/dev/null)
       |    COMPREPLY=( $(compgen -W "$completions" -- "$cur") )
       |    # clean up if no matches were found to prevent default file completion
       |    [[ -z "$COMPREPLY" ]] && COMPREPLY=()
       |}
       |
       |complete -F _deder_completion deder
       |""".stripMargin

  val zshScript: String =
    """|#compdef deder
       |
       |_deder_completion() {
       |    local line curcontext="$curcontext" state
       |    local completions
       |    completions=$(deder complete -s zsh -c "$words" -p "$CURSOR" 2>/dev/null)
       |    compadd -a completions
       |}
       |
       |_deder_completion "$@"
       |""".stripMargin

  val powershellScript: String =
    """|Register-ArgumentCompleter -Native -CommandName deder -ScriptBlock {
       |    param($wordToComplete, $commandAst, $cursorPosition)
       |    $line = $commandAst.ToString()
       |    $completions = deder complete -s powershell -c "$line" -p $cursorPosition 2>$null
       |    if ($completions) {
       |        $completions -split "`n" | ForEach-Object {
       |            [System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterValue', $_)
       |        }
       |    }
       |}
       |""".stripMargin

  val fishScript: String =
    """|function __deder_complete
       |    set -l cmdline (commandline)
       |    set -l cursor (commandline -C)
       |    deder complete -s fish -c "$cmdline" -p $cursor 2>/dev/null
       |end
       |
       |complete -c deder -f -a "(__deder_complete)"
       |""".stripMargin

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
