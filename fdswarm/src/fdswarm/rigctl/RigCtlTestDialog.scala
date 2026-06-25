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
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.Socket

/** Test dialog for verifying rigctld integration.
  *
  * Automatically polls rigctld on open and displays each reading as a table row
  * with columns: Frequency, RigCtl Mode, Contest Mode. Shows the last 25 operations.
  */
@Singleton
class RigCtlTestDialog @Inject()(
    rigCtlManager: RigCtlManager,
    rigctldPoller: RigctldPoller,
    modeMappingManager: ModeMappingManager
) extends LazyStructuredLogging:

  private var stage: Option[Stage] = None
  @volatile private var pollStop   = false
  @volatile private var pollThread: Option[Thread] = None

  def show(ownerWindow: Window): Unit =
    stage match
      case Some(s) => s.requestFocus()
      case None    => buildAndShowStage(ownerWindow)

  private def buildAndShowStage(ownerWindow: Window): Unit =
    rigctldPoller.stop()

    val rows = ObservableBuffer.empty[PollRow]

    val freqCol = new TableColumn[PollRow, String]("Frequency"):
      prefWidth       = 160
      cellValueFactory = row => StringProperty(row.value.frequency)

    val rigCtlModeCol = new TableColumn[PollRow, String]("RigCtl Mode"):
      prefWidth       = 130
      cellValueFactory = row => StringProperty(row.value.rigCtlMode)

    val contestModeCol = new TableColumn[PollRow, String]("Contest Mode"):
      prefWidth       = 130
      cellValueFactory = row => StringProperty(row.value.contestMode)

    val table = new TableView[PollRow](rows):
      columns ++= Seq(freqCol, rigCtlModeCol, contestModeCol)
      columnResizePolicy = TableView.ConstrainedResizePolicy
      maxWidth  = Double.MaxValue
      maxHeight = Double.MaxValue

    def addRow(row: PollRow): Unit =
      Platform.runLater:
        rows.prepend(row)
        if rows.size > 25 then rows.remove(25, rows.size)

    val closeButton = DialogButton("Close", "x-circle", ButtonVariant.Secondary, "Close test dialog")
    closeButton.onAction = _ => stage.foreach(_.close())

    val buttonBar = new HBox(10, closeButton):
      padding = Insets(8, 12, 10, 12)

    val root = new VBox(table, buttonBar):
      VBox.setVgrow(table, Priority.Always)
      padding = Insets(8)

    val dialogScene = new Scene(root, 480, 480)
    UiStyles.applyTo(dialogScene)

    val newStage = new Stage:
      title     = "RigCtl Test"
      initOwner(ownerWindow)
      resizable = true
      minWidth  = 400
      minHeight = 320
      scene     = dialogScene
      onHidden  = _ =>
        stopPoller()
        stage = None
        rigctldPoller.restart()

    newStage.show()
    stage = Some(newStage)
    startPoller(addRow)

  // ── Poller ────────────────────────────────────────────────────────────────

  private def startPoller(addRow: PollRow => Unit): Unit =
    pollStop = false
    val cfg = rigCtlManager.config.value
    val t = new Thread(
      () =>
        while !pollStop && !Thread.currentThread().isInterrupted do
          try
            val socket = new Socket(cfg.host, cfg.port)
            socket.setSoTimeout(2000)
            try
              val out = new PrintWriter(socket.getOutputStream, true)
              val in  = new BufferedReader(new InputStreamReader(socket.getInputStream))
              while !pollStop && !socket.isClosed do
                out.print("f\n"); out.flush()
                val freq = Option(in.readLine()).map(_.trim).getOrElse("?")

                out.print("m\n"); out.flush()
                val mode     = Option(in.readLine()).map(_.trim).getOrElse("?")
                in.readLine() // consume passband line

                val contestMode = modeMappingManager.fromString(mode)
                addRow(PollRow(freq, mode, contestMode.toString))
                Thread.sleep(cfg.pollIntervalMs)
            finally
              socket.close()
          catch
            case _: InterruptedException =>
              Thread.currentThread().interrupt()
            case e: java.net.ConnectException =>
              addRow(PollRow(s"${cfg.host}:${cfg.port}", "connect error", e.getMessage))
              sleepOrInterrupt(3000)
            case e: java.net.SocketTimeoutException =>
              addRow(PollRow("—", "timeout", e.getMessage))
              sleepOrInterrupt(3000)
            case e: Exception =>
              if !pollStop then addRow(PollRow("—", "error", e.getMessage))
              sleepOrInterrupt(3000),
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

/** One row displayed in the RigCtl test table. */
case class PollRow(frequency: String, rigCtlMode: String, contestMode: String)
