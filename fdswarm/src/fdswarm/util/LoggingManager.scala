/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fdswarm.util

import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import io.circe.Printer
import io.circe.parser.*
import io.circe.syntax.*
import jakarta.inject.Inject
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator

/**
 * Manages logging configurations by interacting with Log4j2 and persisting logging levels in a JSON file.
 *
 * @param fileHelper A utility class for handling file-related operations.
 *
 *                   This class provides functionality to:
 *                   - Retrieve the current list of logging configurations.
 *                   - Update the logging level for a specific logger.
 *                   - Remove all custom loggers and reset them to the default level.
 *                   - Apply initial logging configurations from a persisted JSON file.
 *
 *                   Logging configurations are stored in a `logging.json` file and use Log4j2 for runtime level adjustments. */
class LoggingManager @Inject() (
                                 fileHelper: FileHelper
                               ) extends LazyStructuredLogging:
  private val loggingJsonPath = fileHelper.directory / "logging.json"
  private var currentLoggers: List[LoggerLevel] = load()


  def getLoggers: List[LoggerLevel] = currentLoggers

  def updateLogger(
                    loggerName: String,
                    level: LevelEnum
                  ): Unit =
    val normalizedLoggerName = normalizeLoggerName(loggerName)
    val index = currentLoggers.indexWhere(_.logger == normalizedLoggerName)
    if (index >= 0) 
      currentLoggers = currentLoggers.updated(index, LoggerLevel(normalizedLoggerName, level))
    else 
      currentLoggers = currentLoggers :+ LoggerLevel(normalizedLoggerName, level)
    applyToLog4j2(
      normalizedLoggerName,
      level
    )
    save()


  def removeAllLoggers(): Unit =
    currentLoggers.foreach: ll =>
      Configurator.setLevel(
        normalizeLoggerName(ll.logger),
        Level.INFO
      )
    currentLoggers = List.empty
    save()

  private def applyToLog4j2(
                             loggerName: String,
                             level: LevelEnum
                           ): Unit =
    val l4jLevel = Level.toLevel(level.toString, Level.INFO)
    Configurator.setLevel(
      normalizeLoggerName(loggerName),
      l4jLevel
    )

  private def save(): Unit =
    val json = currentLoggers.asJson.printWith(Printer.indented("  "))
    os.write.over(loggingJsonPath, json, createFolders = true)

  private def load(): List[LoggerLevel] =
    if (os.exists(loggingJsonPath)) {
      try {
        val json = os.read(loggingJsonPath)
        decode[List[LoggerLevel]](json) match {
          case Right(loggers) =>
            loggers.map: ll =>
              ll.copy(
                logger = normalizeLoggerName(ll.logger)
              )
          case Left(error) =>
            System.err.println(s"Error decoding logging.json: ${error.getMessage}")
            defaultLoggers
        }
      } catch {
        case e: Exception =>
          System.err.println(s"Error loading logging.json: ${e.getMessage}")
          defaultLoggers
      }
    } else {
      defaultLoggers
    }

  private def defaultLoggers: List[LoggerLevel] = List.empty

  def applyInitialConfig(): Unit =
    if (currentLoggers.nonEmpty) {
      System.out.println("Logging configuration from logging.json:")
      currentLoggers.foreach { ll =>
        System.out.println(s"  ${ll.logger}: ${ll.level}")
        applyToLog4j2(
          ll.logger,
          ll.level
        )
      }
    } else {
      logger.debug("No logging configuration found in logging.json.")
    }

  private def normalizeLoggerName(
                                   loggerName: String
                                 ): String =
    loggerName.trim.stripSuffix("$")
