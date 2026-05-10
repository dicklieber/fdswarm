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

package fdswarm.fx.contest

import com.typesafe.config.ConfigFactory
import fdswarm.model.Callsign
import fdswarm.util.HamPhonetic.fromString
import io.circe.Codec
import scalafx.geometry.Insets
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.GridPane

import scala.jdk.CollectionConverters.*

trait ContestConfigFields:
  def contestType: ContestType
  def ourCallsign: Callsign
  def transmitters: Int
  def ourClass: String
  def ourSection: String
  def toolTip: Node

/**
 * @param transmitters number of transmitters
 * @param ourCallsign  our callsign
 * @param ourClass     our class
 * @param ourSection   our section
 * @param contestType WFD or ARRL
 */
case class ContestConfig(
    contestType: ContestType,
    ourCallsign: Callsign,
    transmitters: Int = 1,
    ourClass: String,
    ourSection: String)
    extends ContestConfigFields
    derives Codec.AsObject, sttp.tapir.Schema:
  require(ourClass.nonEmpty, "ourClass must not be empty")
  require(ourSection.nonEmpty, "ourSection must not be empty")
  val exchange: String =
    s"$transmitters$ourClass $ourSection"

  def toolTip: Node =
    ContestConfig.toolTipGrid(
      "contestType" -> contestType.toString,
      "callsign" -> Option(ourCallsign).map(_.toString).getOrElse(""),
      "transmitters" -> transmitters.toString,
      "ourClass" -> ContestConfig.withDetails(
        ourClass,
        ContestConfig.classDescription(contestType, ourClass)
      ),
      "ourSection" -> ContestConfig.withDetails(
        ourSection,
        ContestConfig.sectionName(ourSection)
      )
    )

  def weAre(usePhonetic: Boolean): String =
    val callsignValue = Option(ourCallsign).map(_.toString).getOrElse("")
    if usePhonetic then
      s"We are ${fromString(callsignValue)} $transmitters ${fromString(ourClass)} ${fromString(ourSection)}"
    else
      s"We are $callsignValue $transmitters$ourClass $ourSection"

  val display: String =
    exchange
  lazy val isDefined:Boolean = contestType != ContestType.NONE

object ContestConfig:
  private lazy val config = ConfigFactory.load()

  val noContest: ContestConfig = ContestConfig(
    contestType = ContestType.NONE,
    ourCallsign = Callsign(""),
    transmitters = 1,
    ourClass = "-",
    ourSection = "-"
  )

  private[contest] def withDetails(
      value: String,
      details: Option[String]
  ): String =
    details.filter(_.nonEmpty).map(detail => s"$value ($detail)").getOrElse(value)

  private[contest] def toolTipGrid(rows: (String, String)*): GridPane =
    val grid = new GridPane:
      hgap = 8
      vgap = 2
      padding = Insets(6)
      styleClass += "contest-config-tooltip"

    rows.zipWithIndex.foreach { case ((key, value), row) =>
      val keyLabel = new Label(key):
        styleClass += "tooltip-key"
      val valueLabel = new Label(value):
        styleClass += "tooltip-value"
      grid.add(keyLabel, 0, row)
      grid.add(valueLabel, 1, row)
    }

    grid

  private[contest] def classDescription(
      contestType: ContestType,
      ourClass: String
  ): Option[String] =
    val classCode = ourClass.dropWhile(_.isDigit)
    Option.when(config.hasPath("fdswarm.contests")) {
      config.getConfigList("fdswarm.contests").asScala
    }.toSeq
      .flatten
      .find(_.getString("name") == contestType.toString)
      .flatMap { contestConfig =>
        contestConfig.getConfigList("classChoices").asScala
          .find(_.getString("ch") == classCode)
          .map(_.getString("description"))
      }

  private[contest] def sectionName(ourSection: String): Option[String] =
    Option.when(config.hasPath("fdswarm.sections")) {
      config.getConfigList("fdswarm.sections").asScala
    }.toSeq
      .flatten
      .flatMap(_.getConfigList("sections").asScala)
      .find(_.getString("code") == ourSection)
      .map(_.getString("name"))
