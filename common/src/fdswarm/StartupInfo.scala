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

package fdswarm
import _root_.io.circe.parser.decode
import fdswarm.StartupConfig
import fdswarm.logging.LazyStructuredLogging
import os.*

/**
 * extracts StartupConfig from the command line arguments.
 * [[StartupConfig]] is set my the deveopment tool manager, used to start multiple instances of the fdswrm.
 *
 * @param rawArgs from command line.
 */
class StartupInfo(rawArgs: Array[String]) extends LazyStructuredLogging():
  val info: Option[StartupConfig] =
    logger.debug(s"Raw command line arguments: [${rawArgs.mkString(", ")}]")
    val startupInfoIdx = rawArgs.indexOf("--startupInfo")
    if startupInfoIdx == -1 then
      logger.debug("No --startupInfo parameter found in command line arguments.")
      None
    else if startupInfoIdx + 1 >= rawArgs.length then
      logger.debug(s"--startupInfo parameter found at position $startupInfoIdx but no file path provided.")
      None
    else
      val pathString = rawArgs(startupInfoIdx + 1)
      logger.debug(s"StartupInfo file path from command line: $pathString")
      try

        val path: Path = os.Path(pathString)
        logger.debug(s"StartupInfo file path resolved to: $path exists: ${os.exists(path)}" )
        val jsonString = os.read(path)
        logger.debug(s"Successfully read ${jsonString.length} characters from startup info file: $pathString")
        decode[StartupConfig](jsonString) match
          case Right(config) =>
            logger.debug(s"Successfully decoded StartupConfig from $pathString: $config")
            Some(config)
          case Left(error) =>
            logger.debug(s"Failed to decode JSON from $pathString to StartupConfig: ${error.getMessage}")
            None
      catch
        case e: Exception =>
          logger.debug(s"Failed to read startup info file '$pathString': ${e.getClass.getSimpleName}: ${e.getMessage}")
          None
  val integrationTest: Option[IntegrationTestConfig] =
    if rawArgs.contains("--integration-test") then
      val settings = info.flatMap(_.integrationTest).getOrElse(
        throw new IllegalArgumentException("--integration-test requires integrationTest settings in --startupInfo"))
      require(java.nio.file.Paths.get(settings.dataDirectory).isAbsolute, "Integration dataDirectory must be absolute")
      require(settings.udpPort > 1024 && settings.udpPort <= 65535 && settings.udpPort != 8090,
        "Integration UDP port must be between 1025 and 65535 and different from the normal port 8090")
      require(settings.token.length >= 32, "Integration token must contain at least 32 characters")
      Some(settings)
    else None
