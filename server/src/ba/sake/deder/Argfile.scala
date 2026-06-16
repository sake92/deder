package ba.sake.deder

/** Writes Java @-files (argument files) to keep JSON messages small.
  *
  * Java's launcher supports `java @filepath` as a shorthand — it reads the file and expands its
  * contents as command-line arguments. This is the standard approach for long classpaths.
  *
  * By offloading the classpath (and JVM options) into a file, we avoid serializing potentially
  * multi-kilobyte strings into `RunSubprocess` JSON messages, which would otherwise exceed
  * avaje-jsonb's hardcoded 50 K string-buffer limit on the client side.
  *
  * File format: one argument per line, newline-separated. Example:
  * {{{
  * -Dfoo=bar
  * -Xmx2g
  * -cp
  * /path/to/jar1.jar:/path/to/jar2.jar
  * }}}
  */
object Argfile {

  /** Write an argument file and return its absolute path.
    *
    * @param dir        Directory to create the file in (typically the task's `ctx.out`).
    * @param key        Stable identifier for the file, used in the filename (`key-jvm-opts.txt`).
    *                   Must consist of alphanumeric characters, dots, hyphens, and underscores.
    * @param jvmOptions JVM options that go before `-cp` (`-Dfoo=bar`, `-Xmx2g`, etc.). May be empty.
    * @param classpath  Ordered classpath entries.
    * @param jvmOptions2 JVM options that go after `-cp` (`--add-opens`, `--add-exports`, etc.). Default empty.
    * @param mainClass  The Java main class. Default empty (omitted from file if empty).
    * @param args       Program arguments (after the main class). Default empty.
    * @return           Absolute path of the created file, ready for use as `@path` in a command.
    */
  def write(
      dir: os.Path,
      key: String,
      jvmOptions: Seq[String],
      classpath: Classpath,
      jvmOptions2: Seq[String] = Seq.empty,
      mainClass: String = "",
      args: Seq[String] = Seq.empty
  ): os.Path = {
    require(key.nonEmpty && key.matches("[a-zA-Z0-9._-]+"), s"Invalid argfile key: '$key'")
    os.makeDir.all(dir)
    val file = dir / s"$key-jvm-opts.txt"
    val cpString = classpath.entries.map(_.toString).mkString(java.io.File.pathSeparator)
    val lines =
      (jvmOptions ++ Seq("-cp", cpString) ++ jvmOptions2 ++ Option.when(mainClass.nonEmpty)(mainClass).toSeq ++ args)
        .map(escapeArg)
    os.write.over(file, lines.mkString("\n"))
    file
  }

  /** Quote arguments that contain whitespace, as required by Java's @-file parser. */
  private def escapeArg(arg: String): String =
    if arg.exists(_.isWhitespace) then s""""$arg""""
    else arg
}
