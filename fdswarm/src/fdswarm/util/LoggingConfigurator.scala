package fdswarm.util

import com.organization.BuildInfo
import fdswarm.io.FileHelper
import fdswarm.logging.StructuredLogger
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory

object LoggingConfigurator:

  def addFileAppender(fileHelper: FileHelper): Unit =

    os.makeDir.all(fileHelper.directory)

    val logFile = fileHelper.directory / s"${BuildInfo.productName}.log"
    val accessLogFile = fileHelper.directory / "access.log"
    os.write.append(logFile, "", createFolders = true)
    os.write.append(accessLogFile, "", createFolders = true)
    StructuredLogger.setJsonEventSink: eventJson =>
      os.write.append(
        logFile,
        eventJson + System.lineSeparator(),
        createFolders = true
      )

    val builder = ConfigurationBuilderFactory.newConfigurationBuilder()
    builder.setStatusLevel(Level.WARN)
    builder.setConfigurationName(s"${BuildInfo.appName}Logging")

    val consoleLayout =
      builder
        .newLayout("PatternLayout")
        .addAttribute("pattern", "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%t] %c - %msg%n")

    val console =
      builder.newAppender("Console", "Console")
    console.addAttribute("target", "SYSTEM_OUT")
    console.add(consoleLayout)
    builder.add(console)

    val accessFile =
      builder.newAppender("AccessFile", "File")
    accessFile.addAttribute("fileName", accessLogFile.toString)
    accessFile.addAttribute("immediateFlush", true)
    accessFile.add(
      builder
        .newLayout("PatternLayout")
        .addAttribute("pattern", "%msg%n")
    )
    builder.add(accessFile)

    builder.add(
      builder
        .newLogger("org.http4s.server.middleware.Logger", Level.INFO)
        .addAttribute("additivity", false)
        .add(builder.newAppenderRef("AccessFile"))
    )

    builder.add(
      builder
        .newRootLogger(Level.INFO)
        .add(builder.newAppenderRef("Console"))
    )

    Configurator.reconfigure(builder.build())
