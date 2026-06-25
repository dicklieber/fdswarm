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
import fdswarm.model.Mode
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.geometry.{HPos, Insets, VPos}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.stage.{Stage, Window}

/** Dialog for editing the rigctl-mode → [[Mode]] token mappings.
  *
  * Each [[Mode]] enum is displayed as a labelled row with a [[TextField]].
  * The field value is a space-separated list of tokens; a token ending with
  * `*` matches any string with that prefix (e.g. `CW*`), `*` alone matches
  * everything, and a plain token requires an exact match.
  *
  * Changes are only committed when the user clicks '''Save'''; '''Cancel'''
  * closes the dialog without altering the stored mappings.
  */
@Singleton
class ModeMappingDialog @Inject()(modeMappingManager: ModeMappingManager)
    extends LazyStructuredLogging:

  private var stage: Option[Stage] = None

  def show(ownerWindow: Window): Unit =
    stage match
      case Some(s) => s.requestFocus()
      case None    => buildAndShowStage(ownerWindow)

  private def buildAndShowStage(ownerWindow: Window): Unit =

    // ── Mode rows ─────────────────────────────────────────────────────────────

    val fields: Seq[(Mode, TextField)] =
      modeMappingManager.orderedMappings.map { (mode, tokens) =>
        val field = new TextField:
          text      = tokens
          maxWidth  = Double.MaxValue
          promptText = "space-separated tokens, use * for prefix match"
        mode -> field
      }

    val hintLabel = new Label(
      "Tokens: exact string (e.g. RTTY) or prefix wildcard (e.g. CW*). " +
      "Use * alone as a catch-all. Separate multiple tokens with spaces."
    ):
      style    = "-fx-font-size: 11px; -fx-text-fill: #555;"
      wrapText = true

    val grid = new GridPane:
      hgap    = 8
      vgap    = 6
      padding = Insets(12)
      columnConstraints = Seq(
        new ColumnConstraints:
          prefWidth    = 60
          halignment   = HPos.Right
          hgrow        = Priority.Never
        ,
        new ColumnConstraints:
          hgrow        = Priority.Always
          fillWidth    = true
      )

    fields.zipWithIndex.foreach { case ((mode, field), row) =>
      val label = new Label(mode.toString + ":")
      GridPane.setValignment(label, VPos.Center)
      grid.add(label, 0, row)
      grid.add(field,  1, row)
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    val saveButton   = DialogButton("Save",   "floppy",   ButtonVariant.Primary,   "Save mode mappings")
    val cancelButton = DialogButton("Cancel", "x-circle", ButtonVariant.Secondary, "Close without saving")

    saveButton.onAction = _ =>
      val newMappings = fields.map { (mode, field) =>
        mode.toString -> field.text.value.trim
      }.toMap
      modeMappingManager.save(newMappings)
      logger.info("Mode mappings saved", "mappings" -> newMappings.toString)
      stage.foreach(_.close())

    cancelButton.onAction = _ => stage.foreach(_.close())

    val buttonBar = new HBox(10, saveButton, cancelButton):
      padding = Insets(8, 12, 10, 12)

    // ── Layout ────────────────────────────────────────────────────────────────

    val root = new VBox(grid, hintLabel, buttonBar):
      padding = Insets(4)
      VBox.setVgrow(grid, Priority.Always)

    val dialogScene = new Scene(root, 480, 240)
    UiStyles.applyTo(dialogScene)

    val newStage = new Stage:
      title    = "Mode Mapping Editor"
      initOwner(ownerWindow)
      resizable = true
      minWidth  = 400
      minHeight = 200
      scene     = dialogScene
      onHidden  = _ => stage = None

    newStage.show()
    stage = Some(newStage)
