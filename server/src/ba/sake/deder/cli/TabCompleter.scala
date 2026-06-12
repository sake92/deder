package ba.sake.deder.cli

import ba.sake.deder.TasksResolver
import scala.util.boundary

class TabCompleter(moduleIds: Seq[String], taskIds: Seq[String], toolNames: Seq[String]) {

  private val allSubcommands = Seq(
    "version",
    "clean",
    "modules",
    "tasks",
    "plugins",
    "plan",
    "exec",
    "shutdown",
    "import",
    "bsp",
    "complete",
    "help",
    "tool"
  )

  enum ValueType:
    case ModuleIds, TaskNames, ShellTypes, ImportFrom, Subcommands, OutputFormats, LogLevels, ToolNames

  case class FlagDef(long: String, short: Option[String], valueType: Option[ValueType])

  private val commandFlags: Map[String, Seq[FlagDef]] = Map(
    "modules" -> Seq(
      FlagDef("--modules", Some("-m"), Some(ValueType.ModuleIds)),
      FlagDef("--depth-down", None, None),
      FlagDef("--depth-up", None, None),
      FlagDef("--format", Some("-f"), Some(ValueType.OutputFormats))
    ),
    "tasks" -> Seq(
      FlagDef("--module", Some("-m"), Some(ValueType.ModuleIds)),
      FlagDef("--format", Some("-f"), Some(ValueType.OutputFormats))
    ),
    "plugins" -> Seq(
      FlagDef("--format", Some("-f"), Some(ValueType.OutputFormats))
    ),
    "plan" -> Seq(
      FlagDef("--modules", Some("-m"), Some(ValueType.ModuleIds)),
      FlagDef("--task", Some("-t"), Some(ValueType.TaskNames)),
      FlagDef("--format", Some("-f"), Some(ValueType.OutputFormats))
    ),
    "clean" -> Seq(
      FlagDef("--modules", Some("-m"), Some(ValueType.ModuleIds)),
      FlagDef("--task", Some("-t"), Some(ValueType.TaskNames))
    ),
    "exec" -> Seq(
      FlagDef("--task", Some("-t"), Some(ValueType.TaskNames)),
      FlagDef("--modules", Some("-m"), Some(ValueType.ModuleIds)),
      FlagDef("--log-level", Some("-l"), Some(ValueType.LogLevels)),
      FlagDef("--format", Some("-f"), Some(ValueType.OutputFormats)),
      FlagDef("--watch", Some("-w"), None)
    ),
    "import" -> Seq(
      FlagDef("--from", None, Some(ValueType.ImportFrom))
    ),
    "complete" -> Seq(
      FlagDef("--shell", Some("-s"), Some(ValueType.ShellTypes)),
      FlagDef("--command-line", Some("-c"), None),
      FlagDef("--cursor-pos", Some("-p"), None),
      FlagDef("--output", Some("-o"), None)
    ),
    "help" -> Seq(
      FlagDef("--command", Some("-c"), Some(ValueType.Subcommands))
    )
  )

  def complete(commandLine: String, cursorPos: Int): Seq[String] = boundary {
    val (args, wordPos) = TabCompleter.shellSplit(commandLine, cursorPos)
    val currentWord = if wordPos >= 0 && wordPos < args.length then args(wordPos) else ""
    val prevWord = if wordPos >= 1 && wordPos < args.length then args(wordPos - 1) else ""

    args match {
      case Seq("deder", subcommand, rest*) =>
        // 1. Check if the previous word was a flag that expects a value
        commandFlags.get(subcommand).foreach { flags =>
          val valueCompletion = flags
            .collectFirst {
              case FlagDef(long, short, Some(valueType)) if prevWord == long || short.exists(_ == prevWord) =>
                valueType
            }
            .flatMap { vt =>
              Some(completeValue(vt, currentWord))
            }
          valueCompletion.foreach(vc => boundary.break(vc))
        }

        // 2. Handle "bsp" subcommand specially (it has sub-subcommand)
        if subcommand == "bsp" then return Seq("install").filter(_.startsWith(currentWord))

        // Handle "tool" subcommand specially — complete configured tool names
        if subcommand == "tool" then
          if rest.length <= 1 then return toolNames.filter(_.startsWith(currentWord))
          else return Seq.empty

        // 3. Complete flags for this subcommand
        commandFlags
          .get(subcommand)
          .map { flags =>
            flags
              .flatMap { f =>
                f.short.toSeq ++ Seq(f.long)
              }
              .filter(_.startsWith(currentWord))
          }
          .getOrElse {
            // Unknown subcommand or subcommand with no flags
            if allSubcommands.contains(subcommand) then Seq.empty
            else allSubcommands.filter(_.startsWith(subcommand))
          }

      case Seq("deder") =>
        allSubcommands

      case Seq(first, _*) =>
        allSubcommands.filter(_.startsWith(first))

      case _ =>
        Seq.empty
    }
  }

  private def completeValue(valueType: ValueType, prefix: String): Seq[String] = {
    val candidates: Seq[String] = valueType match {
      case ValueType.ModuleIds     => moduleIds
      case ValueType.TaskNames     => taskIds
      case ValueType.ShellTypes    => ShellType.values.map(_.toString).toSeq
      case ValueType.ImportFrom    => ImportBuildTool.values.map(_.toString).toSeq
      case ValueType.Subcommands   => allSubcommands
      case ValueType.OutputFormats => Seq("plain", "json", "densejson", "dot", "mermaid")
      case ValueType.LogLevels => Seq("error", "warning", "info", "debug", "trace")
      case ValueType.ToolNames => toolNames
    }
    candidates.filter(_.startsWith(prefix))
  }
}

object TabCompleter {

  def apply(tasksResolver: TasksResolver, toolNames: Seq[String]): TabCompleter =
    new TabCompleter(
      moduleIds = tasksResolver.allModules.map(_.id),
      taskIds = tasksResolver.publicTaskInstancesPerModule.values.flatten.map(_.task.name).toSeq.distinct,
      toolNames = toolNames
    )

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
       |    local -a completions
       |    local raw_output
       |    raw_output=$(deder complete -s zsh -c "${(j: :)words}" -p "$CURSOR" 2>/dev/null)
       |    completions=(${(s: :)raw_output})
       |    if (( ${#completions} > 0 )); then
       |        compadd -a completions
       |    fi
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
