package ba.sake.deder

import java.io.{ByteArrayOutputStream, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import ba.sake.deder.{ServerNotification, ServerNotificationsLogger}

class TeePrintStream(
    original: PrintStream,
    isStdErr: Boolean
) extends PrintStream(new TeePrintStream.TeeOutputStream(original, isStdErr), true) {

  override def flush(): Unit = {
    super.flush()
    val logger = OutputCaptureContext.currentNotificationsLogger.get()
    if (logger != null) {
      val typedLogger = logger.asInstanceOf[ServerNotificationsLogger]
      val baos = TeePrintStream.byteBuffer.get()
      if (baos.size() > 0) {
        TeePrintStream.flushLine(typedLogger, baos.toString(StandardCharsets.UTF_8), isStdErr)
        TeePrintStream.byteBuffer.set(new ByteArrayOutputStream())
      }
    }
  }
}

object TeePrintStream {
  private val MaxBufferSize = 8192

  private val byteBuffer: ThreadLocal[ByteArrayOutputStream] =
    ThreadLocal.withInitial(() => new ByteArrayOutputStream())

  private def flushLine(
      logger: ServerNotificationsLogger,
      line: String,
      isStdErr: Boolean
  ): Unit = {
    val moduleId = Option(OutputCaptureContext.currentModuleId.get())
    logger.add(ServerNotification.logInfo(line, moduleId))
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
      val typedLogger = logger.asInstanceOf[ServerNotificationsLogger]
      val baos = byteBuffer.get()
      if (b == '\n') {
        flushLine(typedLogger, baos.toString(StandardCharsets.UTF_8), isStdErr)
        byteBuffer.set(new ByteArrayOutputStream())
      } else {
        baos.write(b)
        if (baos.size() > MaxBufferSize) {
          flushLine(typedLogger, baos.toString(StandardCharsets.UTF_8), isStdErr)
          byteBuffer.set(new ByteArrayOutputStream())
        }
      }
    }
  }
}
