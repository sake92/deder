package ba.sake.deder

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import com.typesafe.scalalogging.StrictLogging

object LoggingUtils extends StrictLogging {

  def configureLogging(cfg: ServerProperties): Unit = {
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    rootLogger.setLevel(Level.toLevel(cfg.logLevel))

    val loggerContext = rootLogger.getLoggerContext().asInstanceOf[LoggerContext]

    val existingFileAppender = rootLogger.getAppender("FILE") match {
      case rfa: RollingFileAppender[_] => Some(rfa)
      case _ => None
    }

    existingFileAppender match {
      case Some(appender) =>
        logger.info(s"Reconfiguring existing FILE log appender with logRolloverPattern=${cfg.logRolloverPattern}")
        appender.getRollingPolicy() match {
          case rp: TimeBasedRollingPolicy[_] =>
            rp.setFileNamePattern(patternWithLogDir(cfg.logRolloverPattern))
            rp.setMaxHistory(cfg.logMaxHistory)
            rp.setTotalSizeCap(FileSize.valueOf(cfg.logTotalSizeCap))
            rp.start()
          case _ =>
            logger.warn("Existing FILE appender has unknown rolling policy, skipping reconfiguration")
        }

      case None =>
        logger.info(s"Creating FILE log appender with logRolloverPattern=${cfg.logRolloverPattern}")

        val encoder = PatternLayoutEncoder()
        encoder.setContext(loggerContext)
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -%kvp- %msg%n")
        encoder.start()

        val rollingPolicy = TimeBasedRollingPolicy[ILoggingEvent]()
        rollingPolicy.setContext(loggerContext)
        rollingPolicy.setFileNamePattern(patternWithLogDir(cfg.logRolloverPattern))
        rollingPolicy.setMaxHistory(cfg.logMaxHistory)
        rollingPolicy.setTotalSizeCap(FileSize.valueOf(cfg.logTotalSizeCap))

        val appender = RollingFileAppender[ILoggingEvent]()
        appender.setName("FILE")
        appender.setContext(loggerContext)
        appender.setFile(".deder/logs/server.log")
        appender.setEncoder(encoder)
        appender.setRollingPolicy(rollingPolicy)
        rollingPolicy.setParent(appender)
        rollingPolicy.start()
        appender.start()

        rootLogger.detachAppender("FILE")
        rootLogger.addAppender(appender)
    }
  }

  private def patternWithLogDir(pattern: String): String = {
    if pattern.startsWith(".deder/logs/") then pattern
    else s".deder/logs/$pattern"
  }
}
