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

package fdswarm.rigctl

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

  @volatile private var rigctldProcess: Option[os.SubProcess] = None

  /** Called with each non-empty output line from the running rigctld process.
    * Set to [[None]] to stop receiving notifications.
    */
  @volatile var outputListener: Option[String => Unit] = None

  /** Saves a new configuration (triggers auto-persist via the onChange listener). */
  def saveConfig(cfg: RigCtlConfig): Unit =
    config.value = cfg

  /** Returns `true` if something is already listening on `host`:`port`. */
  private def isPortOpen(host: String, port: Int): Boolean =
    try
      val s = new java.net.Socket()
      s.connect(new java.net.InetSocketAddress(host, port), 500)
      s.close()
      true
    catch
      case _: Exception => false

  /** Launches rigctld with the current [[config]] parameters.
    *
    * If [[RigCtlConfig.leaveRigctld]] is `true` and rigctld is already reachable on the
    * configured port, the launch is skipped (the running instance is used as-is).
    * Otherwise any previously-tracked process is killed first and a new one is started.
    */
  def launch(): Either[String, Unit] =
    val cfg = config.value
    if cfg.leaveRigctld && isPortOpen(cfg.host, cfg.port) then
      logger.info(s"rigctld already running at ${cfg.host}:${cfg.port} — skipping launch")
      return Right(())
    kill()
    val cmd = cfg.buildCommand
    logger.info(s"Launching rigctld: ${cfg.commandLine}")
    try
      val proc = os.proc(cmd).spawn(mergeErrIntoOut = true)
      rigctldProcess = Some(proc)
      logger.info("rigctld process started", "pid" -> proc.wrapped.pid())
      startOutputLogger(proc)
      Right(())
    catch
      case e: Exception =>
        rigctldProcess = None
        val msg = s"Failed to launch rigctld: ${e.getMessage}"
        logger.error(msg, e)
        Left(msg)

  private def startOutputLogger(proc: os.SubProcess): Unit =
    val t = new Thread(
      () =>
        val reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(proc.stdout.wrapped))
        var line = reader.readLine()
        while line != null do
          if line.nonEmpty then
            logger.info(s"rigctld: $line")
            outputListener.foreach(_(line))
          line = reader.readLine()
        val exitedClean = proc.waitFor()
        if exitedClean then
          logger.info("rigctld process ended cleanly")
        else
          logger.warn("rigctld process ended with a non-zero exit code")
        outputListener.foreach(_(s"process ended, success=$exitedClean")),
      "rigctld-output"
    )
    t.setDaemon(true)
    t.start()

  /** Kills the rigctld process previously started by [[launch]], if any.
    *
    * If [[RigCtlConfig.leaveRigctld]] is `true` the process is detached (left running)
    * rather than destroyed, so it persists after FdSwarm exits.
    */
  def kill(): Unit =
    rigctldProcess.foreach { proc =>
      if proc.isAlive() then
        if config.value.leaveRigctld then
          logger.info("rigctld process left running (leaveRigctld=true)")
        else
          proc.destroy()
          logger.info("rigctld process stopped")
    }
    rigctldProcess = None

  /** Returns `true` if the rigctld process launched by [[launch]] is currently running. */
  def isRunning: Boolean = rigctldProcess.exists(_.isAlive())

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
      val output = os.proc(rigctlBin, "-l").call(mergeErrIntoOut = true, check = false).out.text()
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
