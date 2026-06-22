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

import fdswarm.bandmodes.SelectedBandModeManager
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.{Band, BandMode, Mode}
import jakarta.inject.{Inject, Singleton}
import scalafx.application.Platform

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.Socket

/** Polls rigctld via TCP and updates [[SelectedBandModeManager.selected]] when the rig's band or mode changes.
  *
  * rigctld (from Hamlib) must be running and accessible at the configured host/port.
  * Configuration is read from [[RigCtlManager]] at each (re)start, so changes made in the
  * RigCtl Manager dialog take effect immediately after calling [[restart]].
  */
@Singleton
class RigctldPoller @Inject()(
    selectedBandModeManager: SelectedBandModeManager,
    rigCtlManager: RigCtlManager
) extends LazyStructuredLogging:

  @volatile private var stopRequested = false
  @volatile private var maybeThread: Option[Thread] = None

  def start(): Unit = this.synchronized {
    val cfg = rigCtlManager.config.value
    if !cfg.enabled then
      logger.info("rigctld polling is disabled")
      return
    if maybeThread.isEmpty then
      stopRequested = false
      val t = new Thread(() => pollLoop(), "rigctld-poller")
      t.setDaemon(true)
      t.start()
      maybeThread = Some(t)
      logger.info(s"rigctld poller started, connecting to ${cfg.host}:${cfg.port} every ${cfg.pollIntervalMs}ms")
  }

  def stop(): Unit = this.synchronized {
    stopRequested = true
    maybeThread.foreach(_.interrupt())
    maybeThread = None
  }

  /** Stops any running poll thread and restarts with the current [[RigCtlManager]] config. */
  def restart(): Unit =
    stop()
    start()

  private def pollLoop(): Unit =
    while !Thread.currentThread().isInterrupted do
      val cfg = rigCtlManager.config.value
      try
        val socket = new Socket(cfg.host, cfg.port)
        socket.setSoTimeout(2000)
        try
          val out = new PrintWriter(socket.getOutputStream, true)
          val in  = new BufferedReader(new InputStreamReader(socket.getInputStream))
          logger.info(s"Connected to rigctld at ${cfg.host}:${cfg.port}")
          innerLoop(out, in, socket)
        finally
          socket.close()
      catch
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
        case e: java.net.ConnectException =>
          logger.debug(s"rigctld not available at ${cfg.host}:${cfg.port} – ${e.getMessage}")
          sleepOrInterrupt(5000)
        case e: Exception =>
          logger.warn(s"rigctld connection error: ${e.getMessage}")
          sleepOrInterrupt(5000)

  private def innerLoop(out: PrintWriter, in: BufferedReader, socket: Socket): Unit =
    while !Thread.currentThread().isInterrupted && !socket.isClosed do
      val cfg = rigCtlManager.config.value
      try
        val freqHz  = queryFrequency(out, in)
        val modeStr = queryMode(out, in)

        for
          hz   <- freqHz
          band <- Band.fromFrequency(hz)
          mode <- modeStr.flatMap(s => scala.util.Try(Mode.fromString(s)).toOption)
        do
          val newBandMode = BandMode(band, mode)
          if newBandMode != selectedBandModeManager.selected.value then
            logger.info(s"rigctld: band/mode changed to $newBandMode")
            Platform.runLater {
              selectedBandModeManager.save(newBandMode)
            }

        Thread.sleep(cfg.pollIntervalMs)
      catch
        case _: java.net.SocketTimeoutException =>
          logger.debug("rigctld read timeout")
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
        case e: java.io.IOException =>
          logger.debug(s"rigctld I/O error (will reconnect): ${e.getMessage}")
          return  // break inner loop → reconnect

  /** Sends `f\n` and returns the frequency in Hz. */
  private def queryFrequency(out: PrintWriter, in: BufferedReader): Option[Long] =
    out.print("f\n")
    out.flush()
    Option(in.readLine()).flatMap(s => scala.util.Try(s.trim.toLong).toOption)

  /** Sends `m\n`, reads mode + passband lines, returns the mode string. */
  private def queryMode(out: PrintWriter, in: BufferedReader): Option[String] =
    out.print("m\n")
    out.flush()
    val modeStr = Option(in.readLine()).map(_.trim)
    in.readLine()  // consume passband width line
    modeStr

  private def sleepOrInterrupt(ms: Long): Unit =
    try Thread.sleep(ms)
    catch case _: InterruptedException => Thread.currentThread().interrupt()
