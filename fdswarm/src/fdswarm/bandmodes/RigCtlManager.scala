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

package fdswarm.bandmodes

import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.ObjectProperty

/** Manages the rigctld configuration and controls the rigctld process lifecycle.
  *
  * Config is persisted automatically to `rigctl-config.json` via [[FileHelper]] whenever
  * [[config]] changes.  Use [[launch]] / [[kill]] to start and stop the rigctld daemon.
  * Use [[listRigs]] to retrieve all Hamlib-supported rig models from `rigctl -l`.
  */
@Singleton
class RigCtlManager @Inject()(fileHelper: FileHelper) extends LazyStructuredLogging:

  private val fileName = "rigctl-config.json"

  /** Current configuration, auto-saved on every change. */
  val config: ObjectProperty[RigCtlConfig] =
    ObjectProperty(fileHelper.loadOrDefault[RigCtlConfig](fileName)(RigCtlConfig()))

  config.onChange { (_, _, newValue) =>
    fileHelper.save(fileName, newValue)
    logger.info("RigCtl config saved", "config" -> newValue.toString)
  }

  @volatile private var rigctldProcess: Option[Process] = None

  /** Saves a new configuration (triggers auto-persist via the onChange listener). */
  def saveConfig(cfg: RigCtlConfig): Unit =
    config.value = cfg

  /** Launches rigctld with the current [[config]] parameters.
    *
    * If rigctld is already running it is killed first.
    */
  def launch(): Either[String, Unit] =
    kill()
    val cfg = config.value
    val cmd = buildCommand(cfg)
    logger.info("Launching rigctld", "command" -> cmd.mkString(" "))
    try
      val pb = new ProcessBuilder(cmd*)
      pb.redirectErrorStream(true)
      val proc = pb.start()
      rigctldProcess = Some(proc)
      logger.info("rigctld process started", "pid" -> proc.pid())
      Right(())
    catch
      case e: Exception =>
        rigctldProcess = None
        val msg = s"Failed to launch rigctld: ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)

  /** Kills the rigctld process previously started by [[launch]], if any. */
  def kill(): Unit =
    rigctldProcess.foreach { proc =>
      if proc.isAlive then
        proc.destroy()
        logger.info("rigctld process stopped")
    }
    rigctldProcess = None

  /** Returns `true` if the rigctld process launched by [[launch]] is currently running. */
  def isRunning: Boolean = rigctldProcess.exists(_.isAlive)

  /** Runs `rigctl -l` and returns all supported rig models, or an error message.
    *
    * The first header line is skipped.  Lines that do not parse are silently ignored.
    */
  def listRigs(): Either[String, Seq[RigInfo]] =
    // Derive rigctl binary from rigctld path: drop the trailing 'd'
    val rigctlBin =
      val d = config.value.rigctldPath
      if d.endsWith("rigctld") then d.dropRight(1)
      else if d.endsWith("rigctld.exe") then d.dropRight(5) + ".exe"
      else "rigctl"

    try
      val proc = new ProcessBuilder(rigctlBin, "-l")
        .redirectErrorStream(true)
        .start()
      val output = scala.io.Source.fromInputStream(proc.getInputStream).mkString
      proc.waitFor()
      val rigs = output.linesIterator
        .drop(1)                          // skip header
        .flatMap(parseRigLine)
        .toSeq
        .sorted
      if rigs.isEmpty then
        Left(s"No rigs found. Is '$rigctlBin' installed and on PATH?")
      else
        Right(rigs)
    catch
      case e: Exception =>
        Left(s"Failed to run '$rigctlBin -l': ${e.getMessage}")

  // ── helpers ──────────────────────────────────────────────────────────────────

  /** Returns the rigctld command as a display string for the given config. */
  def commandLine(cfg: RigCtlConfig): String = buildCommand(cfg).mkString(" ")

  private def buildCommand(cfg: RigCtlConfig): Seq[String] =
    Seq(
      cfg.rigctldPath,
      "-m", cfg.rigModel.toString,
      "-t", cfg.port.toString
    ) ++ (if cfg.rigDevice.nonEmpty then Seq("-r", cfg.rigDevice) else Seq.empty)

  /** Parses one line from `rigctl -l` output.
    * Expected format (fixed-width columns):
    * {{{
    *      1  Hamlib                 Dummy                   0.5             Beta
    * }}}
    */
  private def parseRigLine(line: String): Option[RigInfo] =
    val trimmed = line.trim
    if trimmed.isEmpty then return None
    // Split on 2+ whitespace to handle multi-word manufacturer/model names
    val parts = trimmed.split("\\s{2,}").map(_.trim)
    if parts.length >= 4 then
      scala.util.Try {
        RigInfo(
          modelNumber  = parts(0).toInt,
          manufacturer = parts(1),
          model        = parts(2),
          version      = parts(3),
          status       = if parts.length > 4 then parts(4) else ""
        )
      }.toOption
    else
      None
