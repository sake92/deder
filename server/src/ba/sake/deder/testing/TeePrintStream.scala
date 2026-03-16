package ba.sake.deder.testing

import java.io.{OutputStream, PrintStream}
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class TeePrintStream(
    original: PrintStream,
    isStdErr: Boolean
) extends PrintStream(new TeePrintStream.TeeOutputStream(original, isStdErr), true) {

  override def flush(): Unit = {
    super.flush()
    val logger = OutputCaptureContext.currentNotificationsLogger.get()
    if (logger != null) {
      val sb = TeePrintStream.lineBuffer.get()
      if (sb.nonEmpty) {
        TeePrintStream.flushLine(logger, sb.toString(), isStdErr)
        sb.setLength(0)
      }
    }
  }
}

object TeePrintStream {
  private[testing] val lineBuffer: ThreadLocal[StringBuilder] =
    ThreadLocal.withInitial(() => new StringBuilder())

  private def flushLine(
      logger: ServerNotificationsLogger,
      line: String,
      isStdErr: Boolean
  ): Unit = {
    val moduleId = Option(OutputCaptureContext.currentModuleId.get())
    val prefix = if isStdErr then "[stderr] " else ""
    logger.add(ServerNotification.logInfo(s"$prefix$line", moduleId))
  }

  private class TeeOutputStream(
      original: PrintStream,
      isStdErr: Boolean
  ) extends OutputStream {

    override def write(b: Int): Unit = {
      original.write(b)
      bufferByte(b.toByte)
    }

    override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
      original.write(buf, off, len)
      for (i <- off until off + len) bufferByte(buf(i))
    }

    override def flush(): Unit = {
      original.flush()
    }

    private def bufferByte(b: Byte): Unit = {
      val logger = OutputCaptureContext.currentNotificationsLogger.get()
      if (logger == null) return
      val ch = b.toChar
      val sb = lineBuffer.get()
      if (ch == '\n') {
        flushLine(logger, sb.toString(), isStdErr)
        sb.setLength(0)
      } else {
        sb.append(ch)
      }
    }
  }
}
