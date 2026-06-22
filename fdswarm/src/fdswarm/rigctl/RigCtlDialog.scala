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
import scalafx.beans.property.BooleanProperty
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

/** Dialog for configuring and managing the rigctld integration.
  *
  * Rig model selection is handled by [[RigModelTableEditor]], which is
  * registered as a [[fdswarm.fx.utils.editor.CustomFieldEditor]] for the
  * `rigModel` field.  All other settings are rendered automatically by
  * [[CaseClassPropertyEditor]].
  */
@Singleton
class RigCtlDialog @Inject()(rigCtlManager: RigCtlManager, rigctldPoller: RigctldPoller, rigCtlTestDialog: RigCtlTestDialog)
    extends LazyStructuredLogging:

  private var stage: Option[Stage] = None

  def show(ownerWindow: Window): Unit =
    stage match
      case Some(s) => s.requestFocus()
      case None    => buildAndShowStage(ownerWindow)

  private def buildAndShowStage(ownerWindow: Window): Unit =
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

    // ── Buttons ───────────────────────────────────────────────────────────────

    val saveButton = DialogButton("Save", "floppy", ButtonVariant.Primary, "Save configuration")
    saveButton.disable <== dirty.not()
    saveButton.onAction = _ =>
      rigCtlManager.saveConfig(editor.finish())
      stage.foreach(_.close())

    val closeButton = DialogButton("Close", "x-circle", ButtonVariant.Secondary, "Close without saving")
    closeButton.onAction = _ => stage.foreach(_.close())

    val helpButton = DialogButton("Help", "info-circle", ButtonVariant.Help, "Open Rig Control documentation")
    helpButton.onAction = _ => HelpBrowser.open("/fdswarmDocs/user/rigctl.html")

    val testButton = DialogButton("Test", "bug", ButtonVariant.Warning, "Open RigCtl test dialog")
    testButton.onAction = _ => rigCtlTestDialog.show(ownerWindow)

    // ── Launch / status ───────────────────────────────────────────────────────

    val statusLabel = new Label:
      def refresh(): Unit =
        if rigCtlManager.isRunning then
          text  = "● Running"
          style = "-fx-text-fill: green;"
        else
          text  = "○ Stopped"
          style = "-fx-text-fill: gray;"
      refresh()

    val launchButton = DialogButton("Launch", "play-circle", ButtonVariant.Success, "Launch (or relaunch) rigctld")
    launchButton.onAction = _ =>
      rigCtlManager.launch() match
        case Right(_) =>
          launchButton.text = "Relaunch"
          statusLabel.text  = "● Running"
          statusLabel.style = "-fx-text-fill: green;"
        case Left(err) =>
          statusLabel.text  = s"✗ $err"
          statusLabel.style = "-fx-text-fill: red;"

    if rigCtlManager.isRunning then launchButton.text = "Relaunch"

    // ── Layout ────────────────────────────────────────────────────────────────

    val commandLineBar = new HBox(8, new Label("Command:"), commandLineField):
      padding = Insets(4, 12, 4, 12)
      HBox.setHgrow(commandLineField, Priority.Always)

    val launchBar = new HBox(8, launchButton, statusLabel):
      padding = Insets(4, 12, 4, 12)

    val buttonBar = new HBox(10, saveButton, closeButton, testButton, helpButton):
      padding = Insets(8, 12, 10, 12)

    val root = new VBox(settingsPane, commandLineBar, launchBar, buttonBar):
      VBox.setVgrow(settingsPane, Priority.Always)

    val dialogScene = new Scene(root, 620, 600)
    UiStyles.applyTo(dialogScene)

    val newStage = new Stage:
      title     = "RigCtl Manager"
      initOwner(ownerWindow)
      resizable = true
      minWidth  = 560
      minHeight = 520
      scene     = dialogScene
      onHidden  = _ => stage = None

    newStage.show()
    stage = Some(newStage)
