package fdswarm.util

import fdswarm.io.FileHelper
import fdswarm.logging.StructuredLogger
import munit.FunSuite

class LoggingConfiguratorTest extends FunSuite:
  test("addFileAppender writes structured log events to fdswarm.log"):
    val dir = os.temp.dir(prefix = "fdswarm-log-test", deleteOnExit = true)
    val fileHelper = new FileHelper:
      override val directory: os.Path = dir

    try
      LoggingConfigurator.addFileAppender(fileHelper)

      val logger = StructuredLogger("fdswarm.util.LoggingConfiguratorTest")
      logger.info("test log event", "marker" -> "file-appender")

      val logFile = dir / "fdswarm.log"
      assert(os.exists(logFile))
      val content = os.read(logFile)
      assert(content.contains("test log event"))
      assert(content.contains("file-appender"))
    finally
      if os.exists(dir) then os.remove.all(dir)

  test("configured logger levels keep writing to fdswarm.log"):
    val dir = os.temp.dir(prefix = "fdswarm-log-level-test", deleteOnExit = true)
    val fileHelper = new FileHelper:
      override val directory: os.Path = dir

    try
      os.write.over(
        dir / "logging.json",
        """[
          |  {
          |    "logger": "fdswarm.util.LoggingConfiguratorTest",
          |    "level": "Debug"
          |  }
          |]""".stripMargin,
        createFolders = true
      )

      LoggingConfigurator.addFileAppender(fileHelper)
      val loggingManager = LoggingManager(fileHelper)
      loggingManager.applyInitialConfig()

      val logger = StructuredLogger("fdswarm.util.LoggingConfiguratorTest")
      logger.debug("debug log event", "marker" -> "configured-level")

      val content = os.read(dir / "fdswarm.log")
      assert(content.contains("debug log event"))
      assert(content.contains("configured-level"))
    finally
      if os.exists(dir) then os.remove.all(dir)
