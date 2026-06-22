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

import fdswarm.fx.components.{ButtonVariant, DialogButton}
import fdswarm.fx.utils.UiStyles
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.Socket

/** Test dialog for verifying rigctld integration.
  *
  * Displays rigctld process output and live poll results (raw frequency and mode) in a
  * scrolling log area.  The dialog owns its own polling thread — independent of
  * [[RigctldPoller]] — so it can show raw rigctld responses without affecting the main
  * application state.
  */
@Singleton
class RigCtlTestDialog @Inject()(rigCtlManager: RigCtlManager, rigctldPoller: RigctldPoller) extends LazyStructuredLogging:

  private var stage: Option[Stage] = None
  @volatile private var pollStop    = false
  @volatile private var pollThread: Option[Thread] = None

  def show(ownerWindow: Window): Unit =
    stage match
      case Some(s) => s.requestFocus()
      case None    => buildAndShowStage(ownerWindow)

  private def buildAndShowStage(ownerWindow: Window): Unit =
    rigctldPoller.stop()

    // ── Log area ──────────────────────────────────────────────────────────────

    val logArea = new TextArea:
      editable  = false
      wrapText  = false
      style     = "-fx-font-family: monospace; -fx-font-size: 11;"
      maxWidth  = Double.MaxValue
      maxHeight = Double.MaxValue

    def append(line: String): Unit =
      Platform.runLater:
        logArea.appendText(line + "\n")

    // Forward rigctld process output to the log area; highlight known error patterns
    rigCtlManager.outputListener = Some { line =>
      val isError = line.contains("returning(-") ||
                    line.startsWith("Communication timed out") ||
                    line.contains("rig_open") && line.contains("error") ||
                    line.contains("Cannot connect")
      val prefix = if isError then "[rigctld][ERROR]" else "[rigctld]"
      append(s"$prefix $line")
    }

    // If rigctld is already running, note it
    if rigCtlManager.isRunning then
      append("rigctld is already running (output shown going forward)")
    else
      append("rigctld is not running — press Launch to start it")

    // ── Buttons ───────────────────────────────────────────────────────────────

    val launchButton = DialogButton("Launch", "play-circle", ButtonVariant.Success, "Launch (or relaunch) rigctld")
    launchButton.onAction = _ =>
      append("--- Launching rigctld ---")
      append(s"Command: ${rigCtlManager.commandLine(rigCtlManager.config.value)}")
      rigCtlManager.launch() match
        case Right(_) => append("rigctld process started")
        case Left(err) => append(s"ERROR: $err")

    val testNetButton = DialogButton("Test Radio Network", "wifi", ButtonVariant.Warning, "Test TCP connectivity to the radio device")
    testNetButton.onAction = _ =>
      val cfg = rigCtlManager.config.value
      val device = cfg.rigDevice.trim
      if device.isEmpty then
        append("No radio device configured (rigDevice is empty)")
      else
        val (host, port) =
          if device.contains(":") then
            val parts = device.split(":", 2)
            (parts(0), scala.util.Try(parts(1).toInt).getOrElse(4992))
          else
            (device, 4992)
        append(s"--- Testing TCP connection to $host:$port ---")
        val t = new Thread(
          () =>
            try
              val socket = new java.net.Socket()
              socket.connect(new java.net.InetSocketAddress(host, port), 3000)
              socket.close()
              append(s"[OK] Reachable: $host:$port")
            catch
              case _: java.net.ConnectException =>
                append(s"[FAIL] Connection refused at $host:$port — is the radio on?")
              case _: java.net.SocketTimeoutException =>
                append(s"[FAIL] Timeout connecting to $host:$port — radio not responding on network")
              case e: Exception =>
                append(s"[FAIL] ${e.getMessage}"),
          "rigctld-net-test"
        )
        t.setDaemon(true)
        t.start()

    val pollButton = DialogButton("Start Polling", "activity", ButtonVariant.Primary, "Poll rigctld for frequency and mode")
    pollButton.onAction = _ =>
      if pollThread.isDefined then
        stopPoller()
        pollButton.text = "Start Polling"
      else
        startPoller(append)
        pollButton.text = "Stop Polling"

    val clearButton = DialogButton("Clear", "trash", ButtonVariant.Warning, "Clear the log")
    clearButton.onAction = _ => logArea.clear()

    val closeButton = DialogButton("Close", "x-circle", ButtonVariant.Secondary, "Close test dialog")
    closeButton.onAction = _ => stage.foreach(_.close())

    val buttonBar = new HBox(10, launchButton, testNetButton, pollButton, clearButton, closeButton):
      padding = Insets(8, 12, 10, 12)

    // ── Layout ────────────────────────────────────────────────────────────────

    val root = new VBox(logArea, buttonBar):
      VBox.setVgrow(logArea, Priority.Always)
      padding = Insets(8)

    val dialogScene = new Scene(root, 720, 480)
    UiStyles.applyTo(dialogScene)

    val newStage = new Stage:
      title     = "RigCtl Test"
      initOwner(ownerWindow)
      resizable = true
      minWidth  = 500
      minHeight = 320
      scene     = dialogScene
      onHidden  = _ =>
        stopPoller()
        rigCtlManager.outputListener = None
        stage = None
        rigctldPoller.restart()

    newStage.show()
    stage = Some(newStage)

  // ── Poller ────────────────────────────────────────────────────────────────

  private def startPoller(append: String => Unit): Unit =
    pollStop = false
    val cfg = rigCtlManager.config.value
    val t = new Thread(
      () =>
        append(s"--- Connecting to ${cfg.host}:${cfg.port} ---")
        while !pollStop && !Thread.currentThread().isInterrupted do
          try
            val socket = new Socket(cfg.host, cfg.port)
            socket.setSoTimeout(2000)
            try
              val out = new PrintWriter(socket.getOutputStream, true)
              val in  = new BufferedReader(new InputStreamReader(socket.getInputStream))
              append(s"Connected to rigctld at ${cfg.host}:${cfg.port}")
              while !pollStop && !socket.isClosed do
                out.print("f\n"); out.flush()
                val freq = Option(in.readLine()).map(_.trim).getOrElse("?")
                out.print("m\n"); out.flush()
                val mode     = Option(in.readLine()).map(_.trim).getOrElse("?")
                val passband = Option(in.readLine()).map(_.trim).getOrElse("?")
                append(s"freq=$freq Hz  mode=$mode  passband=$passband")
                Thread.sleep(cfg.pollIntervalMs)
            finally
              socket.close()
          catch
            case _: InterruptedException =>
              Thread.currentThread().interrupt()
            case e: java.net.ConnectException =>
              append(s"Cannot connect to ${cfg.host}:${cfg.port} – ${e.getMessage}")
              sleepOrInterrupt(3000)
            case e: java.net.SocketTimeoutException =>
              append(s"Timeout reading from rigctld – ${e.getMessage}")
              sleepOrInterrupt(3000)
            case e: Exception =>
              if !pollStop then append(s"Error: ${e.getMessage}")
              sleepOrInterrupt(3000)
        append("--- Polling stopped ---"),
      "rigctld-test-poller"
    )
    t.setDaemon(true)
    t.start()
    pollThread = Some(t)

  private def stopPoller(): Unit =
    pollStop = true
    pollThread.foreach(_.interrupt())
    pollThread = None

  private def sleepOrInterrupt(ms: Long): Unit =
    try Thread.sleep(ms)
    catch case _: InterruptedException => Thread.currentThread().interrupt()
