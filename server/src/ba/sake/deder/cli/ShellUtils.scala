package ba.sake.deder.cli

object ShellUtils {

  /** Splits a shell command-line string into tokens, respecting single-quote,
    * double-quote, and backslash-escape semantics. Returns the token list and
    * the index of the token the cursor is positioned within.
    *
    * @param commandLine the full command line to split
    * @param cursorPos   the cursor position (0-based byte index into commandLine)
    * @return a pair of (tokens, cursorTokenIndex) where cursorTokenIndex is -1
    *         if the cursor position precedes the first word
    */
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
    if currentWordIndex == -1 then {
      if cursorPos == commandLine.length then {
        // cursor is at the end but we didn't find the current word
        commandLine.lastOption match {
          case Some(' ') => tokens += "" // add dummy token for completion after a space
          case _         =>
        }
        currentWordIndex = tokens.length - 1
      } else if isCurrentWord && tokens.nonEmpty then {
        // cursor is in the middle of the last token (no trailing whitespace)
        currentWordIndex = tokens.length - 1
      }
    }
    tokens.result() -> currentWordIndex
  }
}
