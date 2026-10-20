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

import com.typesafe.config.Config
import fdswarm.model.Choice
import io.circe.Codec
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.ObjectProperty

case class ClassChoice(
                            ch: String,
                            description: String
                          ) extends Choice[Char] derives Codec.AsObject:
  override val value: Char = ch.head
  override val label: String = s"$ch - $description"

/**
 * As defined in [[application.conf]]
 * @param contestType  [[fdswarm.fx.contest.ContestType.WFD]] or [[fdswarm.fx.contest.ContestType.ARRL]]
 * @param classChoices what classes are allowed in this contest
 */
case class ContestDefinition(
                              name: ContestType,
                              classChoices: Seq[ClassChoice]
                  ) derives Codec.AsObject:
  def isValidClass(classChar: String): Boolean =
    classChoices.exists(_.ch == classChar)
  def classCharsString: String = classChoices.map(_.ch).mkString

@Singleton
final class ContestCatalog @Inject()(config: Config, contestTypes: ContestTypes):
  def this(config: Config) = this(config, new ContestTypes(config))

  val contests: Seq[ContestDefinition] =
    import scala.jdk.CollectionConverters.*
    config.getConfigList("fdswarm.contests").asScala.toSeq.map { contest =>
      val choices = contest.getConfigList("classChoices").asScala.toSeq.map { choice =>
        ClassChoice(choice.getString("ch"), choice.getString("description"))
      }
      ContestDefinition(contestTypes(contest.getString("name")), choices)
    }

  def getContest(contestType: ContestType): Option[ContestDefinition] =
    contests.find(_.name.id == contestType.id)

  def getChars(contestType: ContestType): String =
    getContest(contestType).map(_.classCharsString).getOrElse("")

  def comboBox(contestTypeProperty: ObjectProperty[ContestType]): ClassComboBox =
    new ClassComboBox(this, contestTypeProperty)



