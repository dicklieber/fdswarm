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

package fdswarm.fx.qso

import fdswarm.QsoMetadataManager
import fdswarm.fx.GridColumns
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.*
import fdswarm.rigctl.RigctldPoller
import fdswarm.store.StyledMessage
import fdswarm.util.{Ids, NodeIdentity}
import jakarta.inject.{Inject, Singleton}
import scalafx.application.Platform
import scalafx.beans.property.BooleanProperty
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.scene.layout.{GridPane, VBox}

import java.time.{LocalDate, LocalTime, ZoneOffset, ZonedDateTime}
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * Panel shown above QsoEntryPanel when Post-Contest mode is active.
 *
 * Provides Operator, Date (yyyyMMdd UTC), and Time (HHmm UTC) fields so
 * that paper-log QSOs can be entered with a custom timestamp.  The QSO
 * itself is still submitted via QsoEntryPanel; this panel only supplies
 * the extra metadata.
 *
 * RigctldPoller is paused while the mode is active and resumed on exit.
 */
@Singleton
class PostContestPanel @Inject()(
    rigctldPoller: RigctldPoller,
    qsoMetadataManager: QsoMetadataManager
) extends LazyStructuredLogging:

  val PostNode: NodeIdentity = NodeIdentity(
    hostIp     = "0.0.0.0",
    port       = 0,
    hostName   = "-post-",
    instanceId = Ids.generateId()
  )

  /** Toggled by the Admin → Post-Contest mode CheckMenuItem. */
  val isActiveProperty: BooleanProperty = BooleanProperty(false)

  /** Wired by ContestEntry to move focus to QsoEntryPanel's callsign field. */
  var onReadyForQso: () => Unit = () => {}

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  private val timeFormatter = DateTimeFormatter.ofPattern("HHmm")

  // ── fields ────────────────────────────────────────────────────────────
  private val operatorField = new TextField {
    promptText      = "Operator Callsign"
    prefColumnCount = 10
  }

  private val dateField = new TextField {
    promptText      = "yyyyMMdd (UTC)"
    prefColumnCount = 10
  }

  private val timeField = new TextField {
    promptText      = "HHmm (UTC)"
    prefColumnCount = 6
  }

  private val statusLabel = new Label

  // ── event handlers ────────────────────────────────────────────────────
  isActiveProperty.onChange { (_, _, active) =>
    if (active) {
      operatorField.text = qsoMetadataManager.qsoMetadata.value.station.operator.value
      dateField.text     = dateFormatter.format(LocalDate.now(ZoneOffset.UTC))
      timeField.text     = ""
      statusLabel.text   = ""
      rigctldPoller.pause()
      Platform.runLater { focusDate() }
    } else {
      rigctldPoller.resume()
    }
  }

  // Date Enter: keep date, clear and focus time
  dateField.onAction = _ =>
    Platform.runLater {
      timeField.text = ""
      timeField.requestFocus()
    }

  // Time Enter: hand off to QsoEntryPanel callsign
  timeField.onAction = _ =>
    Platform.runLater { onReadyForQso() }

  // ── query API used by QsoEntryPanel ──────────────────────────────────
  def isActive: Boolean = isActiveProperty.value

  def stamp: Option[java.time.Instant] =
    try {
      val date = LocalDate.parse(dateField.text.value.trim, dateFormatter)
      val time = LocalTime.parse(timeField.text.value.trim, timeFormatter)
      Some(ZonedDateTime.of(date, time, ZoneOffset.UTC).toInstant)
    } catch {
      case _: DateTimeParseException => None
    }

  def isStampValid: Boolean = stamp.isDefined

  def operatorCallsign: Callsign = {
    val t = operatorField.text.value.trim
    if (Callsign.isValid(t)) Callsign(t) else qsoMetadataManager.qsoMetadata.value.station.operator
  }

  def makePostMetadata(): QsoMetadata =
    qsoMetadataManager.qsoMetadata.value.copy(
      station = qsoMetadataManager.qsoMetadata.value.station.copy(operator = operatorCallsign),
      node    = PostNode
    )

  def focusDate(): Unit = {
    dateField.requestFocus()
    dateField.selectAll()
  }

  def showStatus(msg: StyledMessage): Unit =
    Platform.runLater {
      statusLabel.text = msg.text
      statusLabel.styleClass.clear()
      statusLabel.styleClass.add(msg.css)
    }

  // ── node ──────────────────────────────────────────────────────────────
  private val headerGrid = new GridPane {
    hgap = 10
    vgap = 5
    add(new Label("Operator:"),   0, 0)
    add(operatorField,            1, 0)
    add(new Label("Date (UTC):"), 2, 0)
    add(dateField,                3, 0)
    add(new Label("Time (UTC):"), 4, 0)
    add(timeField,                5, 0)
  }

  private val _node = new VBox(4) {
    children = Seq(
      GridColumns.fieldSet("Post-Contest Entry", headerGrid),
      statusLabel
    )
    visible  <== isActiveProperty
    managed  <== isActiveProperty
  }

  def node: Node = _node
