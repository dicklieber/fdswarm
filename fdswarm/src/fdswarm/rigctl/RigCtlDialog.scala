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
import fdswarm.fx.utils.editor.{CaseClassPropertyEditor, HelpBrowser}
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.application.Platform
import scalafx.beans.property.{BooleanProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import java.net.Socket

/** Dialog for configuring and managing the rigctld integration.
  *
  * Rig model selection is handled by [[RigModelTableEditor]], which is
  * registered as a [[fdswarm.fx.utils.editor.CustomFieldEditor]] for the
  * `rigModel` field.  All other settings are rendered automatically by
  * [[CaseClassPropertyEditor]].
  *
  * The dialog also embeds a live poll table (frequency, rigctl mode, contest
  * mode) so the user can verify connectivity without opening a separate window.
  * The main [[RigctldPoller]] is paused while the dialog is open; a local
  * poller feeds the table instead.  On close the main poller resumes.
  */
@Singleton
class RigCtlDialog @Inject()(
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

    val cfg = rigCtlManager.config.value

    // ── Property editor ───────────────────────────────────────────────────────

    val editor = new CaseClassPropertyEditor[RigCtlConfig](cfg)
    editor.setCustomEditor("rigModel", new RigModelTableEditor(rigCtlManager))

    val settingsPane = editor.vertical
    settingsPane.padding = Insets(12, 12, 4, 12)

    // ── Command line display ──────────────────────────────────────────────────

    val commandLineField = new TextField:
      editable = false
      text     = rigCtlManager.commandLine(cfg)
      style    = "-fx-font-family: monospace; -fx-background-color: #f4f4f4;"
      maxWidth = Double.MaxValue

    val dirty = BooleanProperty(false)

    editor.onAnyFieldChange { () =>
      val current = editor.finish()
      commandLineField.text = rigCtlManager.commandLine(current)
      dirty.value = current != cfg
    }

    // ── Test table ────────────────────────────────────────────────────────────

    val rows = ObservableBuffer.empty[PollRow]

    val freqCol = new TableColumn[PollRow, String]("Frequency"):
      prefWidth        = 160
      cellValueFactory = row => StringProperty(row.value.frequency)

    val rigCtlModeCol = new TableColumn[PollRow, String]("RigCtl Mode"):
      prefWidth        = 130
      cellValueFactory = row => StringProperty(row.value.rigCtlMode)

    val contestModeCol = new TableColumn[PollRow, String]("Contest Mode"):
      prefWidth        = 130
      cellValueFactory = row => StringProperty(row.value.contestMode)

    val table = new TableView[PollRow](rows):
      columns ++= Seq(freqCol, rigCtlModeCol, contestModeCol)
      columnResizePolicy = TableView.ConstrainedResizePolicy
      maxWidth  = Double.MaxValue
      maxHeight = Double.MaxValue

    def addRow(row: PollRow): Unit =
      Platform.runLater:
        rows.prepend(row)
        while rows.size > 25 do rows.remove(rows.size - 1)

    // ── Buttons ───────────────────────────────────────────────────────────────

    val saveRestartButton = DialogButton("Save & Restart", "floppy", ButtonVariant.Primary, "Save configuration and restart poller")
    saveRestartButton.disable <== dirty.not()
    saveRestartButton.onAction = _ =>
      rigCtlManager.saveConfig(editor.finish())
      stopPoller()
      rows.clear()
      startPoller(addRow)

    val closeButton = DialogButton("Close", "x-circle", ButtonVariant.Secondary, "Close without saving")
    closeButton.onAction = _ => stage.foreach(_.close())

    val helpButton = DialogButton("Help", "info-circle", ButtonVariant.Help, "Open Rig Control documentation")
    helpButton.onAction = _ => HelpBrowser.open("/fdswarmDocs/user/rigctl.html")

    // ── Layout ────────────────────────────────────────────────────────────────

    val commandLineBar = new HBox(8, new Label("Command:"), commandLineField):
      padding = Insets(4, 12, 4, 12)
      HBox.setHgrow(commandLineField, Priority.Always)

    val indicator = new RigctlPollerIndicator(rigctldPoller)

    val buttonBar = new HBox(10, indicator, saveRestartButton, closeButton, helpButton):
      padding = Insets(8, 12, 10, 12)

    val root = new VBox(settingsPane, commandLineBar, table, buttonBar):
      VBox.setVgrow(settingsPane, Priority.Always)
      VBox.setVgrow(table, Priority.Always)

    val dialogScene = new Scene(root, 620, 700)
    UiStyles.applyTo(dialogScene)

    val newStage = new Stage:
      title     = "RigCtl Manager"
      initOwner(ownerWindow)
      resizable = true
      minWidth  = 560
      minHeight = 560
      scene     = dialogScene
      onHidden  = _ =>
        stopPoller()
        stage = None
        rigctldPoller.restart()

    newStage.show()
    stage = Some(newStage)
    startPoller(addRow)

  // ── Local test poller ─────────────────────────────────────────────────────

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
                val mode = Option(in.readLine()).map(_.trim).getOrElse("?")
                in.readLine() // consume passband line

                val contestMode = modeMappingManager.fromString(mode)
                addRow(PollRow(freq, mode, contestMode.toString))
                Platform.runLater {
                  rigctldPoller.pollCount.value = rigctldPoller.pollCount.value + 1
                  rigctldPoller.lastError.value = None
                }
                Thread.sleep(cfg.pollIntervalMs)
            finally
              socket.close()
          catch
            case _: InterruptedException =>
              Thread.currentThread().interrupt()
            case e: java.net.ConnectException =>
              addRow(PollRow(s"${cfg.host}:${cfg.port}", "connect error", e.getMessage))
              Platform.runLater { rigctldPoller.lastError.value = Some(s"${cfg.host}:${cfg.port} – ${e.getMessage}") }
              sleepOrInterrupt(3000)
            case e: java.net.SocketTimeoutException =>
              addRow(PollRow("—", "timeout", e.getMessage))
              Platform.runLater { rigctldPoller.lastError.value = Some(s"Read timeout: ${e.getMessage}") }
              sleepOrInterrupt(3000)
            case e: Exception =>
              if !pollStop then addRow(PollRow("—", "error", e.getMessage))
              Platform.runLater { rigctldPoller.lastError.value = Some(e.getMessage) }
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
