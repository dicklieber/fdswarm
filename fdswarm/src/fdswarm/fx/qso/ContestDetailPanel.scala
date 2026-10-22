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

import fdswarm.fx.GridColumns
import fdswarm.fx.contest.ContestConfigManager
import fdswarm.fx.discovery.ContestConfigDialog
import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.DurationFormat
import jakarta.inject.{Inject, Named, Singleton}
import scalafx.animation.{KeyFrame, Timeline}
import scalafx.scene.Node
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.VBox
import scalafx.util.Duration

import java.time.{LocalDateTime, ZonedDateTime, Duration as JDuration}

@Singleton
class ContestDetailPanel @Inject()(
                                    contestManager: ContestConfigManager,
                                    contestConfigDialog: ContestConfigDialog,
                                    @Named("fdswarm.contestTimerUpdateSec") contestTimerUpdateSec: Int
                                 ) extends LazyStructuredLogging:
  private val timerTimeline = new Timeline:
    keyFrames = Seq(
      KeyFrame(Duration(contestTimerUpdateSec * 1000), onFinished = _ => updateContestTimeDisplay())
    )
    cycleCount = Timeline.Indefinite

  private val _node = new VBox()

  private val setupContestLabel = new Label:
    text = "Contest has not been setup."
    styleClass += "contest-timer"
    styleClass += "contest-before"
    minWidth = scalafx.scene.layout.Region.USE_PREF_SIZE

  private val setupContestButton = new Button("Setup Contest"):
    onAction = _ => contestConfigDialog.show()

  private val setupContestContent = new VBox:
    spacing = 8
    children = Seq(
      setupContestLabel,
      setupContestButton
    )

  private def refreshDisplay(): Unit =
    val config = contestManager.contestConfigProperty.value
    if config.isEmpty then
      _node.children = Seq(
        GridColumns.fieldSet(
          "Contest ?",
          setupContestContent
        )
      )
      timerTimeline.stop()
    else
      val current = config.get
      updateContestTimeDisplay()
      _node.children = Seq(
        GridColumns.fieldSet(
          current.contestType.displayName,
          contestTimerLabel
        )
      )
      if current.endTime.isDefined then timerTimeline.play()
      else timerTimeline.stop()

  contestManager.contestConfigProperty.onChange((_, _, _) => refreshDisplay())

  private val contestTimerLabel = new Label:
    styleClass += "contest-timer"
    wrapText = true
    minWidth = 0
    maxWidth = Double.MaxValue
    minHeight = scalafx.scene.layout.Region.USE_PREF_SIZE

  private var useMockTime: Boolean = false
  private var mockTime: ZonedDateTime = ZonedDateTime.now()

  def setMockTime(useFixed: Boolean, time: ZonedDateTime): Unit =
    useMockTime = useFixed
    mockTime = time
    updateContestTimeDisplay()

  private def updateContestTimeDisplay(): Unit =
    val message = contestManager.contestConfigProperty.value.flatMap { config =>
      val now = if useMockTime then mockTime.toLocalDateTime else LocalDateTime.now()
      ContestDetailPanel.endMessage(config.contestType.displayName, config.endTime, now)
    }
    contestTimerLabel.visible = message.isDefined
    contestTimerLabel.managed = message.isDefined
    contestTimerLabel.styleClass.removeAll("contest-before", "contest-during", "contest-after")
    contestTimerLabel.text = message.map(_._1).getOrElse("")
    message.foreach { (_, style) => contestTimerLabel.styleClass.add(style) }

  refreshDisplay()

  def node: Node = _node

object ContestDetailPanel:
  private[qso] def endMessage(
      contestName: String,
      endTime: Option[LocalDateTime],
      now: LocalDateTime): Option[(String, String)] =
    endTime.map { end =>
      if now.isBefore(end) then
        (s"$contestName ends in ${DurationFormat(JDuration.between(now, end))}", "contest-during")
      else
        (s"$contestName ended ${DurationFormat(JDuration.between(end, now))} ago.", "contest-after")
    }
