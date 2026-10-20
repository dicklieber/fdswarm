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

import fdswarm.ContestDates
import fdswarm.fx.GridColumns
import fdswarm.fx.contest.{ContestConfigManager, ContestType}
import fdswarm.fx.discovery.ContestConfigDialog
import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.DurationFormat
import jakarta.inject.{Inject, Named, Singleton}
import scalafx.animation.{KeyFrame, Timeline}
import scalafx.scene.Node
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.VBox
import scalafx.util.Duration

import java.time.{ZonedDateTime, Duration as JDuration}
import scala.compiletime.uninitialized

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

  private var contestType: ContestType = uninitialized
  private var contestDates: ContestDates = uninitialized
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
      contestType = config.get.contestType
      contestDates = contestType.dates()
      updateContestTimeDisplay()
      _node.children = Seq(
        GridColumns.fieldSet(
          s"${contestType.displayName} ${contestDates.startUtc.getYear}",
          contestTimerLabel
        )
      )
      timerTimeline.play()

  contestManager.contestConfigProperty.onChange((_, _, _) => refreshDisplay())

  private val contestTimerLabel = new Label:
    styleClass += "contest-timer"
    minWidth = scalafx.scene.layout.Region.USE_PREF_SIZE

  enum TimeMode:
    case Before, During, After

  private var useMockTime: Boolean = false
  private var mockTime: ZonedDateTime = ZonedDateTime.now()

  def setMockTime(useFixed: Boolean, time: ZonedDateTime): Unit =
    useMockTime = useFixed
    mockTime = time
    updateContestTimeDisplay()

  /**
   * show contest time stuff.
   *
   * @param contestDates
   */
  private def updateContestTimeDisplay(): Unit =
    if !contestManager.hasConfiguration.value then return
    val now = if useMockTime then mockTime else ZonedDateTime.now()

    val mode =
      if now.isBefore(contestDates.startUtc) then TimeMode.Before
      else if now.isAfter(contestDates.endUtc) then TimeMode.After
      else TimeMode.During

    val (msg, style) = mode match
      case TimeMode.Before =>
        (s"${contestType.displayName} ${contestDates.startUtc.getYear} starts in ${DurationFormat(JDuration.between(now, contestDates.startUtc))}", "contest-before")
      case TimeMode.After =>
        (s"${contestType.displayName} ${contestDates.startUtc.getYear} ended ${DurationFormat(JDuration.between(contestDates.endUtc, now))} ago.", "contest-after")
      case TimeMode.During =>
        (s"${contestType.displayName} ${contestDates.startUtc.getYear} ends in ${DurationFormat(JDuration.between(now, contestDates.endUtc))}", "contest-during")

    contestTimerLabel.text = msg
    contestTimerLabel.styleClass.removeAll("contest-before", "contest-during", "contest-after")
    contestTimerLabel.styleClass.add(style)

  refreshDisplay()

  def node: Node = _node
