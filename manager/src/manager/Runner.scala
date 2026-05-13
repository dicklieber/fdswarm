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

package manager

import _root_.io.circe.syntax.*
import fdswarm.logging.LazyStructuredLogging
import fdswarm.StartupConfig
import fdswarm.DirectoryProvider
import jakarta.inject.Inject

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.IndexedSeqView
import scala.jdk.OptionConverters.*

/** create a JSON file of [[fdswarm.StartupConfig]] Starts an instance of the FDSwarm
  * application. pass reference to that file on the command line.
  *
  * @param directoryProvider where manager puts it's files.
  */
class Runner @Inject() (directoryProvider: DirectoryProvider)
    extends LazyStructuredLogging:

  private var instances:Seq[AppInstance] = Seq.empty
  private val edebugConfigDir = directoryProvider() / "debugConfigs"
  private val jarManager = FdswarmJarManager()

  def verifyRequiredJar(): Boolean =
    val missingJar = jarManager.jarInfo().isEmpty
    if missingJar then
      logger.error(
        s"Required jar not found at ${jarManager.jarPath}. Run ./mill fdswarm.assembly to build it."
      )
    !missingJar

  def start(view: IndexedSeqView[StartupConfig]): Unit =
    synchronized {
      if !verifyRequiredJar() then
        sys.exit(1)

      stop()
      os.remove.all(edebugConfigDir)

      val ports = new AtomicInteger(8080)
      instances = (
        for
          startupConfig <- view.iterator
          if startupConfig.enable
        yield
          val pathToJson = edebugConfigDir / s"${startupConfig.id}.json"
          os.write.over(pathToJson, startupConfig.asJson.spaces2, createFolders = true)
          val sJsonPath = pathToJson.toString
          AppInstance(sJsonPath, startupConfig, ports.getAndIncrement())

        ).toSeq
    }

  def stop(): Unit =
    synchronized {
      instances.foreach { instance =>
        try
          instance.stop()
        catch
          case e: Exception =>
            logger.error("Failed to stop managed instance cleanly", e)
      }
      killManagedProcessesByCommandLine()
      instances = Seq.empty
    }

  private def killManagedProcessesByCommandLine(): Unit =
    val marker = edebugConfigDir.toString
    ProcessHandle.allProcesses().forEach { ph =>
      val cmdLine = ph.info().commandLine().toScala.getOrElse("")
      if ph.isAlive && cmdLine.contains("--startupInfo") && cmdLine.contains(marker) then
        try
          logger.info(s"Force-killing managed process pid=${ph.pid()}")
          ph.descendants().forEach { child =>
            if child.isAlive then child.destroyForcibly()
          }
          ph.destroyForcibly()
        catch
          case e: Exception =>
            logger.error(s"Failed to force-kill managed process pid=${ph.pid()}", e)
    }
